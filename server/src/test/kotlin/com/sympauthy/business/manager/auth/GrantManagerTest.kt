package com.sympauthy.business.manager.auth

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.GrantMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantOpenedForResult
import com.sympauthy.business.model.grant.GrantRevocationCriteria
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.grant.GrantStatus
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.AuthorizationCodeConfig
import com.sympauthy.config.model.ByPasswordConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.GrantConfig
import com.sympauthy.config.model.InteractiveFlowConfig
import com.sympauthy.config.model.TokenConfig
import com.sympauthy.data.model.GrantEntity
import com.sympauthy.data.repository.GrantRepository
import com.sympauthy.data.repository.ObjectLockRepository
import com.sympauthy.monitoring.telemetry.LockMetrics
import com.sympauthy.monitoring.telemetry.TokenMetrics
import io.micronaut.data.exceptions.DataIntegrityViolationException
import io.micronaut.data.exceptions.EntityExistsException
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class GrantManagerTest {

    private val grantRepository = mockk<GrantRepository>()

    private val objectLockRepository = mockk<ObjectLockRepository>(relaxed = true)

    /** Relaxed: every account a grant names here is a promoted one, unless a test says otherwise. */
    private val userManager = mockk<UserManager>(relaxed = true)

    private val tokenMetrics = spyk(TokenMetrics(OpenTelemetry.noop()))

    private fun grantManager(grantExpiration: Duration? = null) = GrantManager(
        grantRepository = grantRepository,
        grantMapper = GrantMapper(),
        lockManager = LockManager(objectLockRepository, HeldStripes(), LockMetrics(OpenTelemetry.noop())),
        userManager = userManager,
        uncheckedAuthConfig = authConfig(grantExpiration),
        tokenMetrics = tokenMetrics
    )

    private fun authConfig(grantExpiration: Duration?): AuthConfig = EnabledAuthConfig(
        issuer = "https://issuer.example.com",
        token = TokenConfig(
            accessExpiration = Duration.ofHours(1),
            idExpiration = Duration.ofHours(1),
            refreshEnabled = true,
            refreshExpiration = Duration.ofDays(30),
            dpopRequired = false
        ),
        authorizationCode = AuthorizationCodeConfig(expiration = Duration.ofMinutes(30)),
        interactiveFlow = InteractiveFlowConfig(expiration = Duration.ofMinutes(30)),
        grant = GrantConfig(expiration = grantExpiration),
        identifierClaims = emptyList(),
        userMergingEnabled = false,
        byPassword = ByPasswordConfig(enabled = false)
    )

    /** Stub the save to answer the row it was given, with the identifier the database would have set. */
    private fun captureSave(): CapturingSlot<GrantEntity> {
        val saved = slot<GrantEntity>()
        coEvery { grantRepository.save(capture(saved)) } answers {
            saved.captured.apply { id = UUID.randomUUID() }
        }
        return saved
    }

    /** A `client_credentials` token of the acting client, under the grant identified by [grantId]. */
    private fun mockActorToken(grantId: UUID, expirationDate: LocalDateTime?): AuthenticationToken = mockk {
        every { grant } returns mockk {
            every { id } returns grantId
            every { clientId } returns "acting-client"
        }
        every { this@mockk.expirationDate } returns expirationDate
    }

    private fun session(sessionId: UUID): InteractiveFlowSession = mockk { every { id } returns sessionId }

    private fun oauth2(sessionId: UUID) = InteractiveFlowSessionOAuth2(
        sessionId = sessionId,
        clientId = "client",
        redirectUri = "https://client.example.com/callback",
        requestedScopes = listOf("openid")
    )

    private fun mockGrant(userId: UUID?): Grant = mockk {
        every { id } returns UUID.randomUUID()
        every { this@mockk.userId } returns userId
        every { clientId } returns "client"
    }

    @Test
    fun `createAuthorizationCodeGrant - Opens a grant holding what the request record holds`() = runTest {
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val authenticationDate = LocalDateTime.now().minusMinutes(5)
        val oauth2 = InteractiveFlowSessionOAuth2(
            sessionId = sessionId,
            clientId = "client",
            redirectUri = "https://client.example.com/callback",
            requestedScopes = listOf("openid", "email"),
            requestedClaims = RequestedClaims.of(
                idTokenClaimIds = arrayOf("email"),
                userInfoClaimIds = arrayOf("name")
            ),
            authenticationDate = authenticationDate,
            consentedScopes = listOf("email"),
            consentedAt = authenticationDate,
            consentedBy = ConsentedBy.USER,
            grantedScopes = listOf("openid"),
            grantedAt = authenticationDate,
            grantedBy = GrantedBy.AUTO
        )
        val saved = captureSave()

        val grant = grantManager().createAuthorizationCodeGrant(session(sessionId), oauth2, userId, "audience")

        assertEquals(userId, grant.userId)
        assertEquals("client", grant.clientId)
        assertEquals("audience", grant.audienceId)
        assertEquals(GrantType.AUTHORIZATION_CODE, grant.grantType)
        assertEquals(listOf("openid"), grant.grantedScopes)
        assertEquals(GrantedBy.AUTO, grant.grantedBy)
        assertEquals(listOf("email"), grant.consentedScopes)
        assertEquals(ConsentedBy.USER, grant.consentedBy)
        assertEquals(authenticationDate, grant.authenticationDate)
        assertEquals(oauth2.requestedClaims, grant.requestedClaims)
        assertEquals(sessionId, grant.sessionId)
        assertNull(grant.actorGrantId)
        assertEquals("authorization_code", saved.captured.grantType)
        assertArrayEquals(arrayOf("email"), saved.captured.requestedIdTokenClaims)
    }

    @Test
    fun `createAuthorizationCodeGrant - Answers a session that already opened its grant as a lost completion`() =
        runTest {
            val sessionId = UUID.randomUUID()
            coEvery { grantRepository.save(any()) } throws EntityExistsException(
                "duplicate key", R2dbcDataIntegrityViolationException("23505")
            )

            val exception = assertThrows<BusinessException> {
                grantManager().createAuthorizationCodeGrant(
                    session(sessionId), oauth2(sessionId), UUID.randomUUID(), "audience"
                )
            }

            assertEquals("auth.interactive_flow_session.concurrent_modification", exception.detailsId)
            assertEquals(mapOf("sessionId" to sessionId.toString()), exception.values)
        }

    @Test
    fun `createAuthorizationCodeGrant - Lets a refusal of another kind travel as it is`() = runTest {
        val sessionId = UUID.randomUUID()
        coEvery { grantRepository.save(any()) } throws DataIntegrityViolationException(
            "foreign key", R2dbcDataIntegrityViolationException("23503")
        )

        assertThrows<DataIntegrityViolationException> {
            grantManager().createAuthorizationCodeGrant(
                session(sessionId), oauth2(sessionId), UUID.randomUUID(), "audience"
            )
        }
    }

    @Test
    fun `createClientCredentialsGrant - Opens a grant holding the client scopes and no person`() = runTest {
        captureSave()

        val grant = grantManager().createClientCredentialsGrant("client", "audience", listOf("users:read"))

        assertNull(grant.userId)
        assertEquals(GrantType.CLIENT_CREDENTIALS, grant.grantType)
        assertEquals(listOf("users:read"), grant.clientScopes)
        assertNull(grant.authenticationDate)
        assertNull(grant.sessionId)
    }

    @Test
    fun `createTokenExchangeGrant - Opens a grant for the acting client recording the grant exchanged`() =
        runTest {
            val userId = UUID.randomUUID()
            val actorGrantId = UUID.randomUUID()
            val actorToken = mockActorToken(actorGrantId, expirationDate = null)
            captureSave()

            val grant = grantManager().createTokenExchangeGrant(userId, "audience", actorToken)

            assertEquals(userId, grant.userId)
            assertEquals("acting-client", grant.clientId)
            assertEquals(GrantType.TOKEN_EXCHANGE, grant.grantType)
            assertEquals(actorGrantId, grant.actorGrantId)
            assertEquals(emptyList<String>(), grant.allScopes)
            assertNull(grant.authenticationDate)
        }

    @Test
    fun `createTokenExchangeGrant - Expires the grant with the token exchanged where it expires first`() =
        runTest {
            val actorExpirationDate = LocalDateTime.now().plusMinutes(5)
            captureSave()

            val grant = grantManager(grantExpiration = Duration.ofHours(1)).createTokenExchangeGrant(
                UUID.randomUUID(), "audience", mockActorToken(UUID.randomUUID(), actorExpirationDate)
            )

            assertEquals(actorExpirationDate, grant.expirationDate)
        }

    @Test
    fun `createTokenExchangeGrant - Keeps the configured lifetime where it ends before the token exchanged`() =
        runTest {
            captureSave()

            val grant = grantManager(grantExpiration = Duration.ofMinutes(5)).createTokenExchangeGrant(
                UUID.randomUUID(), "audience", mockActorToken(UUID.randomUUID(), LocalDateTime.now().plusHours(1))
            )

            assertEquals(grant.creationDate.plusMinutes(5), grant.expirationDate)
        }

    @Test
    fun `createGrant - Refuses an account a sign-up has not finished, before writing anything`() = runTest {
        val userId = UUID.randomUUID()
        val actorToken = mockActorToken(UUID.randomUUID(), expirationDate = null)
        coEvery { userManager.checkPromoted(userId) } throws businessExceptionOf("user.not_promoted")

        val exception = assertThrows<BusinessException> {
            grantManager().createTokenExchangeGrant(userId, "audience", actorToken)
        }

        assertEquals("user.not_promoted", exception.detailsId)
        coVerify(exactly = 0) { grantRepository.save(any()) }
    }

    @Test
    fun `createGrant - Gives the grant the lifetime the configuration sets from when it was opened`() = runTest {
        captureSave()

        val grant = grantManager(grantExpiration = Duration.ofHours(1))
            .createClientCredentialsGrant("client", "audience", emptyList())

        assertEquals(grant.creationDate.plusHours(1), grant.expirationDate)
    }

    @Test
    fun `createGrant - Gives the grant no lifetime of its own where the configuration sets none`() = runTest {
        captureSave()

        val grant = grantManager().createClientCredentialsGrant("client", "audience", emptyList())

        assertNull(grant.expirationDate)
    }

    @Test
    fun `revokeGrant - Revokes the grant and the grants exchanged from it holding the lock over the account`() =
        runTest {
            val userId = UUID.randomUUID()
            val grant = mockGrant(userId)
            val revokedAt = slot<LocalDateTime>()
            val cascadeRevokedAt = slot<LocalDateTime>()
            coEvery { grantRepository.updateRevokedAt(grant.id, capture(revokedAt), "CLIENT", null) } returns 1
            coEvery {
                grantRepository.updateRevokedAtByActorGrantId(grant.id, capture(cascadeRevokedAt), "CLIENT", null)
            } returns 0

            grantManager().revokeGrant(grant, GrantRevokedBy.CLIENT, null)

            assertEquals(revokedAt.captured, cascadeRevokedAt.captured)
            coVerifyOrder {
                objectLockRepository.lock(LockKey.User(userId).stripe)
                grantRepository.updateRevokedAt(grant.id, any(), "CLIENT", null)
                grantRepository.updateRevokedAtByActorGrantId(grant.id, any(), "CLIENT", null)
            }
        }

    @Test
    fun `revokeGrant - Takes no lock for a grant no person is behind`() = runTest {
        val grant = mockGrant(null)
        coEvery { grantRepository.updateRevokedAt(grant.id, any(), "CLIENT", null) } returns 1
        coEvery { grantRepository.updateRevokedAtByActorGrantId(grant.id, any(), "CLIENT", null) } returns 2

        grantManager().revokeGrant(grant, GrantRevokedBy.CLIENT, null)

        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    @Test
    fun `revokeGrant - Counts the grant under its client, and the grants exchanged from it under none`() = runTest {
        val grant = mockGrant(null)
        coEvery { grantRepository.updateRevokedAt(grant.id, any(), "AUTHORIZATION_CODE_REPLAYED", null) } returns 1
        coEvery {
            grantRepository.updateRevokedAtByActorGrantId(grant.id, any(), "AUTHORIZATION_CODE_REPLAYED", null)
        } returns 2

        grantManager().revokeGrant(grant, GrantRevokedBy.AUTHORIZATION_CODE_REPLAYED, null)

        verify {
            tokenMetrics.recordRevocations("authorization_code_replayed", "client", 1)
            tokenMetrics.recordRevocations("authorization_code_replayed", null, 2)
        }
    }

    /** An `authorization_code` row of [userId], opened for [clientId]. */
    private fun authorizationCodeEntity(userId: UUID, clientId: String) = GrantEntity(
        userId = userId,
        clientId = clientId,
        audienceId = "audience",
        grantType = "authorization_code",
        grantedScopes = emptyArray(),
        consentedScopes = arrayOf("openid"),
        clientScopes = emptyArray(),
        sessionId = UUID.randomUUID(),
        creationDate = LocalDateTime.now().minusDays(1)
    ).apply { id = UUID.randomUUID() }

    @Test
    fun `revokeGrants - Revokes the grants every criterion reaches, at one date, holding the lock over each account`() =
        runTest {
            val ana = UUID.randomUUID()
            val bob = UUID.randomUUID()
            val adminId = UUID.randomUUID()
            val anaWeb = authorizationCodeEntity(ana, "web")
            val anaMobile = authorizationCodeEntity(ana, "mobile")
            val bobWeb = authorizationCodeEntity(bob, "web")
            coEvery { grantRepository.findAll() } returns flowOf(anaWeb, anaMobile, bobWeb, clientCredentialsEntity())
            val revokedAt = mutableListOf<LocalDateTime>()
            coEvery { grantRepository.updateRevokedAt(any(), capture(revokedAt), "ADMIN", adminId) } returns 1
            coEvery { grantRepository.updateRevokedAtByActorGrantId(any(), any(), "ADMIN", adminId) } returns 0

            val count = grantManager().revokeGrants(
                GrantRevocationCriteria(userIds = setOf(ana, bob), clientIds = setOf("web")),
                GrantRevokedBy.ADMIN,
                adminId
            )

            assertEquals(2, count)
            assertEquals(1, revokedAt.distinct().size)
            coVerify(exactly = 2) { grantRepository.updateRevokedAt(any(), any(), any(), any()) }
            coVerifyOrder {
                objectLockRepository.lock(any())
                grantRepository.updateRevokedAt(anaWeb.id!!, any(), "ADMIN", adminId)
                grantRepository.updateRevokedAt(bobWeb.id!!, any(), "ADMIN", adminId)
            }
            coVerify {
                objectLockRepository.lock(LockKey.User(ana).stripe)
                objectLockRepository.lock(LockKey.User(bob).stripe)
            }
        }

    @Test
    fun `revokeGrants - Counts the grants exchanged from a grant reached, and none no longer live`() = runTest {
        val grant = clientCredentialsEntity()
        coEvery { grantRepository.findAll() } returns flowOf(grant)
        coEvery { grantRepository.updateRevokedAt(grant.id!!, any(), "ADMIN", null) } returns 0
        coEvery { grantRepository.updateRevokedAtByActorGrantId(grant.id!!, any(), "ADMIN", null) } returns 2

        val count = grantManager().revokeGrants(
            GrantRevocationCriteria(grantIds = setOf(grant.id!!)),
            GrantRevokedBy.ADMIN,
            null
        )

        assertEquals(2, count)
    }

    @Test
    fun `revokeGrants - Refuses criteria narrowing nothing, and revokes no grant`() = runTest {
        // The repository is left unstubbed on purpose: reaching the assertion is proof it was never asked.
        val exception = assertThrows<BusinessException> {
            grantManager().revokeGrants(GrantRevocationCriteria(), GrantRevokedBy.ADMIN, null)
        }

        assertEquals("grant.revoke.no_criterion", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `revokeGrantsByUserAndAudience - Revokes at the date given holding the lock over the account`() = runTest {
        val userId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val revokedAt = LocalDateTime.now()
        coEvery {
            grantRepository.updateRevokedAtByUserIdAndAudienceIdAndActorGrantIdIsNull(
                userId, "audience", revokedAt, "CONSENT_REVOKED", adminId
            )
        } returns 3

        val count = grantManager().revokeGrantsByUserAndAudience(
            userId = userId,
            audienceId = "audience",
            revokedAt = revokedAt,
            revokedBy = GrantRevokedBy.CONSENT_REVOKED,
            revokedById = adminId
        )

        assertEquals(3, count)
        coVerifyOrder {
            objectLockRepository.lock(LockKey.User(userId).stripe)
            grantRepository.updateRevokedAtByUserIdAndAudienceIdAndActorGrantIdIsNull(
                userId, "audience", revokedAt, "CONSENT_REVOKED", adminId
            )
        }
    }

    @Test
    fun `findByIdOrNull - Returns null where there is no grant`() = runTest {
        val id = UUID.randomUUID()
        coEvery { grantRepository.findById(id) } returns null

        assertNull(grantManager().findByIdOrNull(id))
    }

    @Test
    fun `findByIdOrNull - Returns the grant the row describes`() = runTest {
        val saved = captureSave()
        val created = grantManager().createClientCredentialsGrant("client", "audience", emptyList())
        coEvery { grantRepository.findById(created.id) } returns saved.captured

        assertEquals(created, grantManager().findByIdOrNull(created.id))
    }

    /** A `client_credentials` row, revoked where [revokedAt] is given and ending at [expirationDate]. */
    private fun clientCredentialsEntity(
        expirationDate: LocalDateTime? = null,
        revokedAt: LocalDateTime? = null
    ) = GrantEntity(
        userId = null,
        clientId = "client",
        audienceId = "audience",
        grantType = "client_credentials",
        grantedScopes = emptyArray(),
        consentedScopes = emptyArray(),
        clientScopes = emptyArray(),
        creationDate = LocalDateTime.now().minusDays(1),
        expirationDate = expirationDate,
        revokedAt = revokedAt,
        revokedBy = revokedAt?.let { GrantRevokedBy.ADMIN.name }
    ).apply { id = UUID.randomUUID() }

    @Test
    fun `getStatus - Read a grant a revocation would reach as live`() {
        val grant = GrantMapper().toGrant(clientCredentialsEntity())

        assertEquals(GrantStatus.LIVE, grantManager().getStatus(grant, live = true, LocalDateTime.now()))
    }

    @Test
    fun `getStatus - Read a revoked grant as revoked, even past its lifetime`() {
        val now = LocalDateTime.now()
        val grant = GrantMapper().toGrant(
            clientCredentialsEntity(expirationDate = now.minusMinutes(1), revokedAt = now.minusHours(1))
        )

        assertEquals(GrantStatus.REVOKED, grantManager().getStatus(grant, live = false, now))
    }

    @Test
    fun `getStatus - Read a grant past its lifetime as expired rather than lapsed`() {
        val now = LocalDateTime.now()
        val grant = GrantMapper().toGrant(clientCredentialsEntity(expirationDate = now.minusMinutes(1)))

        assertEquals(GrantStatus.EXPIRED, grantManager().getStatus(grant, live = false, now))
    }

    @Test
    fun `getStatus - Read a grant neither revoked nor past its lifetime, holding nothing, as lapsed`() {
        val now = LocalDateTime.now()
        val grant = GrantMapper().toGrant(clientCredentialsEntity(expirationDate = now.plusDays(1)))

        assertEquals(GrantStatus.LAPSED, grantManager().getStatus(grant, live = false, now))
    }

    @Test
    fun `listWithStatus - Read every grant, each live where a revocation now would reach it`() = runTest {
        val live = clientCredentialsEntity()
        val lapsed = clientCredentialsEntity()
        coEvery { grantRepository.findAll() } returns flowOf(live, lapsed)
        coEvery { grantRepository.findLiveAt(any()) } returns listOf(live)

        val result = grantManager().listWithStatus()

        assertEquals(
            mapOf(live.id to GrantStatus.LIVE, lapsed.id to GrantStatus.LAPSED),
            result.associate { it.grant.id to it.status }
        )
    }

    @Test
    fun `findWithStatusByIdOrNull - Return null where there is no grant`() = runTest {
        val id = UUID.randomUUID()
        coEvery { grantRepository.findById(id) } returns null

        assertNull(grantManager().findWithStatusByIdOrNull(id))
    }

    @Test
    fun `findWithStatusByIdOrNull - Read the grant live where a revocation now would reach it`() = runTest {
        val entity = clientCredentialsEntity()
        coEvery { grantRepository.findById(entity.id!!) } returns entity
        coEvery { grantRepository.findLiveByIdAt(entity.id!!, any()) } returns entity

        assertEquals(GrantStatus.LIVE, grantManager().findWithStatusByIdOrNull(entity.id!!)?.status)
    }

    @Test
    fun `verifyOpenedFor - Accept a grant opened for the client and its audience`() {
        val result = grantManager().verifyOpenedFor(
            grantOpenedFor("client", "audience"),
            clientOf("client", "audience")
        )

        assertEquals(GrantOpenedForResult.Opened, result)
    }

    @Test
    fun `verifyOpenedFor - Refuse a grant opened for another client`() {
        val client = mockk<Client> { every { id } returns "another-client" }

        val result = grantManager().verifyOpenedFor(mockk { every { clientId } returns "client" }, client)

        assertEquals(GrantOpenedForResult.OpenedForAnotherClient("client", "another-client"), result)
    }

    @Test
    fun `verifyOpenedFor - Refuse a grant whose client has moved to another audience`() {
        val result = grantManager().verifyOpenedFor(
            grantOpenedFor("client", "audience"),
            clientOf("client", "another-audience")
        )

        assertEquals(GrantOpenedForResult.OpenedForAnotherAudience("audience", "client", "another-audience"), result)
    }

    @Test
    fun `toOAuth2Exception - Answer a refusal as invalid_grant under its own code and values`() {
        val exception = GrantOpenedForResult.OpenedForAnotherAudience("audience", "client", "another-audience")
            .toOAuth2Exception()

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.mismatching_audience", exception.detailsId)
        assertEquals("description.grant.mismatching_audience", exception.descriptionId)
        assertEquals("another-audience", exception.values["clientAudienceId"])
    }

    @Test
    fun `checkLive - Refuse a grant that was revoked`() {
        val grant = mockk<Grant> { every { revoked } returns true }

        val exception = assertThrows<OAuth2Exception> { grantManager().checkLive(grant) }

        assertEquals("grant.revoked", exception.detailsId)
    }

    @Test
    fun `checkLive - Refuse a grant past its lifetime`() {
        val grant = mockk<Grant> {
            every { revoked } returns false
            every { expired } returns true
        }

        val exception = assertThrows<OAuth2Exception> { grantManager().checkLive(grant) }

        assertEquals("grant.expired", exception.detailsId)
    }

    @Test
    fun `checkLive - Accept a grant neither revoked nor past its lifetime`() {
        val grant = mockk<Grant> {
            every { revoked } returns false
            every { expired } returns false
        }

        grantManager().checkLive(grant)
    }

    private fun grantOpenedFor(clientId: String, audienceId: String): Grant = mockk {
        every { this@mockk.clientId } returns clientId
        every { this@mockk.audienceId } returns audienceId
    }

    private fun clientOf(id: String, audienceId: String): Client = mockk {
        every { this@mockk.id } returns id
        every { audience.id } returns audienceId
    }

    /** A grant asked for nothing but the account it names. */
    private fun grantNaming(userId: UUID?): Grant = mockk { every { this@mockk.userId } returns userId }

    @Test
    fun `checkUserPromoted - Accept a grant naming an account a sign-up finished`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk()

        grantManager().checkUserPromoted(grantNaming(userId))
    }

    @Test
    fun `checkUserPromoted - Refuse a grant naming an account no sign-up finished`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<OAuth2Exception> { grantManager().checkUserPromoted(grantNaming(userId)) }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.invalid_user", exception.detailsId)
        assertEquals("description.grant.invalid_user", exception.descriptionId)
    }

    @Test
    fun `checkUserPromoted - Accept a grant naming no user without reading an account`() = runTest {
        grantManager().checkUserPromoted(grantNaming(userId = null))

        coVerify(exactly = 0) { userManager.findByIdOrNull(any()) }
    }

}
