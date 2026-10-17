package com.sympauthy.business.manager.auth

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.GrantMapper
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.Grant
import com.sympauthy.business.model.oauth2.GrantRevokedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.AuthorizationCodeConfig
import com.sympauthy.config.model.ByPasswordConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.TokenConfig
import com.sympauthy.data.model.GrantEntity
import com.sympauthy.data.repository.GrantRepository
import com.sympauthy.data.repository.ObjectLockRepository
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun grantManager(grantExpiration: Duration? = null) = GrantManager(
        grantRepository = grantRepository,
        grantMapper = GrantMapper(),
        lockManager = LockManager(objectLockRepository, HeldStripes()),
        userManager = userManager,
        uncheckedAuthConfig = authConfig(grantExpiration)
    )

    private fun authConfig(grantExpiration: Duration?): AuthConfig = EnabledAuthConfig(
        issuer = "https://issuer.example.com",
        token = TokenConfig(
            accessExpiration = Duration.ofHours(1),
            idExpiration = Duration.ofHours(1),
            refreshEnabled = true,
            refreshExpiration = Duration.ofDays(30),
            grantExpiration = grantExpiration,
            dpopRequired = false
        ),
        authorizationCode = AuthorizationCodeConfig(expiration = Duration.ofMinutes(30)),
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

    private fun mockGrant(userId: UUID?): Grant = mockk {
        every { id } returns UUID.randomUUID()
        every { this@mockk.userId } returns userId
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

        val grant = grantManager().createAuthorizationCodeGrant(oauth2, userId, "audience")

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
            val actorGrant = mockk<Grant> {
                every { id } returns actorGrantId
                every { clientId } returns "acting-client"
            }
            captureSave()

            val grant = grantManager().createTokenExchangeGrant(userId, "audience", actorGrant)

            assertEquals(userId, grant.userId)
            assertEquals("acting-client", grant.clientId)
            assertEquals(GrantType.TOKEN_EXCHANGE, grant.grantType)
            assertEquals(actorGrantId, grant.actorGrantId)
            assertEquals(emptyList<String>(), grant.allScopes)
            assertNull(grant.authenticationDate)
        }

    @Test
    fun `createGrant - Refuses an account a sign-up has not finished, before writing anything`() = runTest {
        val userId = UUID.randomUUID()
        val actorGrant = mockk<Grant> {
            every { id } returns UUID.randomUUID()
            every { clientId } returns "acting-client"
        }
        coEvery { userManager.checkPromoted(userId) } throws businessExceptionOf("user.not_promoted")

        val exception = assertThrows<BusinessException> {
            grantManager().createTokenExchangeGrant(userId, "audience", actorGrant)
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
    fun `revokeGrantsByUser - Revokes holding the lock over the account`() = runTest {
        val userId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        coEvery { grantRepository.updateRevokedAtByUserId(userId, any(), "ADMIN", adminId) } returns 2

        assertEquals(2, grantManager().revokeGrantsByUser(userId, GrantRevokedBy.ADMIN, adminId))

        coVerifyOrder {
            objectLockRepository.lock(LockKey.User(userId).stripe)
            grantRepository.updateRevokedAtByUserId(userId, any(), "ADMIN", adminId)
        }
    }

    @Test
    fun `revokeGrantsByUserAndClient - Revokes holding the lock over the account`() = runTest {
        val userId = UUID.randomUUID()
        coEvery {
            grantRepository.updateRevokedAtByUserIdAndClientId(userId, "client", any(), "ADMIN", null)
        } returns 1

        assertEquals(1, grantManager().revokeGrantsByUserAndClient(userId, "client", GrantRevokedBy.ADMIN, null))

        coVerifyOrder {
            objectLockRepository.lock(LockKey.User(userId).stripe)
            grantRepository.updateRevokedAtByUserIdAndClientId(userId, "client", any(), "ADMIN", null)
        }
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
}
