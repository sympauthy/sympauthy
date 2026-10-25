package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.manager.auth.ActorGrantValidator
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.PUBLIC_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.ObjectLockRepository
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*

@Suppress("unused")
@ExtendWith(MockKExtension::class)
class TokenManagerTest {

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var accessTokenGenerator: AccessTokenGenerator

    @MockK
    lateinit var refreshTokenGenerator: RefreshTokenGenerator

    @MockK
    lateinit var idTokenGenerator: IdTokenGenerator

    @MockK
    lateinit var claimAclManager: ClaimAclManager

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var actorGrantValidator: ActorGrantValidator

    /**
     * The real manager behind a spy rather than a mock: the binding rule a refresh asks it is a function of the
     * token alone, so every refresh scenario runs it instead of stubbing an answer to it.
     */
    @SpyK
    var dpopManager = DpopManager(EnabledUrlsConfig(root = URI.create("https://auth.example.com")))

    @MockK
    lateinit var authorizationCodeManager: AuthorizationCodeManager

    /**
     * The real manager behind a spy rather than a mock: whether a grant is live and opened for the client
     * presenting it are functions of the grant and the client alone, so every scenario runs them instead of
     * stubbing an answer to them.
     */
    @SpyK
    var grantManager = GrantManager(mockk(), mockk(), mockk(), mockk(), mockk())

    private val objectLockRepository = mockk<ObjectLockRepository>(relaxed = true)

    /** The real manager over the stripe table, so a test can say which row was taken before what. */
    var lockManager: LockManager = LockManager(objectLockRepository, HeldStripes())

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var tokenMapper: AuthenticationTokenMapper

    @SpyK
    @InjectMockKs
    lateinit var tokenManager: TokenManager

    private fun mockClientWithGrantTypes(vararg grantTypes: GrantType): Client {
        return mockk {
            every { supportsGrantType(any()) } answers { grantTypes.contains(firstArg()) }
            every { audience } returns mockk {
                every { id } returns "test-audience"
            }
        }
    }

    private fun mockRefreshableToken(clientId: String, userId: UUID?): AuthenticationToken {
        val grant = mockk<Grant> {
            every { this@mockk.clientId } returns clientId
            every { this@mockk.userId } returns userId
            every { audienceId } returns "test-audience"
        }
        return mockk {
            every { this@mockk.grant } returns grant
            every { dpopJkt } returns null
        }
    }

    private fun mockClient(clientId: String): Client {
        return mockk {
            every { id } returns clientId
            every { audience } returns mockk {
                every { id } returns "test-audience"
            }
        }
    }

    @Test
    fun `findById - Returns the token with the grant it was issued under`() = runTest {
        val tokenId = UUID.randomUUID()
        val grantId = UUID.randomUUID()
        val entity = mockk<AuthenticationTokenEntity> { every { this@mockk.grantId } returns grantId }
        val grant = mockk<Grant>()
        val token = mockk<AuthenticationToken>()
        coEvery { tokenRepository.findById(tokenId) } returns entity
        coEvery { grantManager.findByIdOrNull(grantId) } returns grant
        every { tokenMapper.toToken(entity, grant) } returns token

        assertSame(token, tokenManager.findById(tokenId))
    }

    @Test
    fun `findById - Returns null when the grant and the token were collected between the two reads`() = runTest {
        val tokenId = UUID.randomUUID()
        val grantId = UUID.randomUUID()
        val entity = mockk<AuthenticationTokenEntity> { every { this@mockk.grantId } returns grantId }
        coEvery { tokenRepository.findById(tokenId) } returns entity andThen null
        coEvery { grantManager.findByIdOrNull(grantId) } returns null

        assertNull(tokenManager.findById(tokenId))
        coVerify(exactly = 0) { tokenMapper.toToken(any(), any()) }
    }

    @Test
    fun `findById - Hands the mapper the missing grant of a token still there`() = runTest {
        val tokenId = UUID.randomUUID()
        val grantId = UUID.randomUUID()
        val entity = mockk<AuthenticationTokenEntity> { every { this@mockk.grantId } returns grantId }
        val failure = BusinessException(recoverable = false, detailsId = "mapper.authentication_token.invalid_property")
        coEvery { tokenRepository.findById(tokenId) } returns entity
        coEvery { grantManager.findByIdOrNull(grantId) } returns null
        every { tokenMapper.toToken(entity, null) } throws failure

        val exception = assertThrows<BusinessException> { tokenManager.findById(tokenId) }

        assertSame(failure, exception)
    }

    /** A code naming a grant of its own, live for another minute unless [expirationDate] says otherwise. */
    private fun code(expirationDate: LocalDateTime = now().plusMinutes(1)) = AuthorizationCode(
        grantId = UUID.randomUUID(),
        code = "code",
        codeChallenge = "challenge",
        codeChallengeMethod = CodeChallengeMethod.S256,
        nonce = "nonce",
        creationDate = expirationDate.minusMinutes(1),
        expirationDate = expirationDate
    )

    /** The grant [code] names as the caller read it, before the lock: it is asked for nothing but its account. */
    private fun grantReadBeforeTheLock(userId: UUID): Grant = mockk { every { this@mockk.userId } returns userId }

    /** Stubs [code] as naming [grant] once the lock is held, and [userId] as an account a sign-up finished. */
    private fun stubExchangeableCode(code: AuthorizationCode, userId: UUID, grant: Grant) {
        coJustRun { userManager.checkPromoted(userId) }
        coEvery { authorizationCodeManager.fetchGrant(code) } returns grant
    }

    /** The doors every token of one response comes through. */
    private val doors = mockk<ClaimAclTokenDoors>()

    /**
     * Stubs the doors of a token issued under [grant] to [client], bound where [dpopJkt] is set, as [doors]: a
     * generator handed any other set was handed doors the response did not build for it.
     */
    private fun stubDoors(grant: Grant, client: Client, dpopJkt: String?) {
        every { grant.consentedScopes } returns emptyList()
        every { grant.grantedScopes } returns emptyList()
        every { grant.clientScopes } returns emptyList()
        every { grant.authenticationDate } returns null
        val context = ClaimAclContext.of(grant, client, dpopJkt)
        every { claimAclManager.getTokenDoors(context, any()) } returns doors
    }

    private fun mockGrant(
        revoked: Boolean = false,
        expired: Boolean = false,
        grantType: GrantType? = null
    ): Grant = mockk {
        every { this@mockk.revoked } returns revoked
        if (!revoked) every { this@mockk.expired } returns expired
        grantType?.let { every { this@mockk.grantType } returns it }
    }

    @Test
    fun `generateTokensForUser - Throws internal for a grant opened for no user, and spends nothing`() = runTest {
        val grant = mockk<Grant> {
            every { id } returns UUID.randomUUID()
            every { userId } returns null
        }

        val exception = assertThrows<BusinessException> {
            tokenManager.generateTokensForUser(code(), grant, mockk())
        }

        assertEquals("code.missing_user", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
        coVerify(exactly = 0) { authorizationCodeManager.markExchanged(any()) }
    }

    @Test
    fun `generateTokensForUser - Generate all tokens under the grant the code names`() = runTest {
        val userId = UUID.randomUUID()
        val code = code()
        val client = mockClientWithGrantTypes(*GrantType.entries.toTypedArray())
        val audience = client.audience
        val grant = mockGrant(grantType = GrantType.AUTHORIZATION_CODE)
        val accessToken = mockk<EncodedAuthenticationToken>()
        val refreshToken = mockk<EncodedAuthenticationToken>()
        val idToken = mockk<EncodedAuthenticationToken>()

        stubExchangeableCode(code, userId, grant)
        coEvery { authorizationCodeManager.markExchanged(code) } returns true
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.AUTHORIZATION_CODE, null)
        } returns accessToken
        coEvery {
            refreshTokenGenerator.generateRefreshToken(grant, audience, GrantType.AUTHORIZATION_CODE, null)
        } returns refreshToken
        coEvery {
            idTokenGenerator.generateIdToken(
                grant,
                match { it.id == "test-audience" },
                accessToken,
                doors,
                GrantType.AUTHORIZATION_CODE,
                "nonce"
            )
        } returns idToken

        val result = tokenManager.generateTokensForUser(code, grantReadBeforeTheLock(userId), client)

        assertSame(accessToken, result.accessToken)
        assertSame(refreshToken, result.refreshToken)
        assertSame(idToken, result.idToken)
        verify(exactly = 1) { claimAclManager.getTokenDoors(any(), any()) }
        coVerifyOrder {
            objectLockRepository.lock(LockKey.User(userId).stripe)
            authorizationCodeManager.fetchGrant(code)
            authorizationCodeManager.markExchanged(code)
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.AUTHORIZATION_CODE, null)
        }
    }

    @Test
    fun `generateTokensForUser - Refuses an account no sign-up finished, and spends nothing`() = runTest {
        val userId = UUID.randomUUID()
        coEvery { userManager.checkPromoted(userId) } throws BusinessException(
            recoverable = false, detailsId = "user.not_promoted"
        )

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.generateTokensForUser(code(), grantReadBeforeTheLock(userId), mockk())
        }

        assertEquals("grant.invalid_user", exception.detailsId)
        coVerify(exactly = 0) { authorizationCodeManager.markExchanged(any()) }
    }

    @Test
    fun `generateTokensForUser - Refuses as expired a code the cleaner collected with its grant meanwhile`() = runTest {
        val userId = UUID.randomUUID()
        val code = code()
        coJustRun { userManager.checkPromoted(userId) }
        coEvery {
            authorizationCodeManager.fetchGrant(code)
        } throws oauth2ExceptionOf(INVALID_GRANT, "token.expired")

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.generateTokensForUser(code, grantReadBeforeTheLock(userId), mockk())
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
        coVerify(exactly = 0) { authorizationCodeManager.markExchanged(any()) }
        coVerify(exactly = 0) { grantManager.revokeGrant(any(), any(), any()) }
    }

    @Test
    fun `generateTokensForUser - Revokes the grant the code names and issues none for a code already exchanged`() =
        runTest {
            val userId = UUID.randomUUID()
            val code = code()
            val grant = mockk<Grant>()
            stubExchangeableCode(code, userId, grant)
            coEvery { authorizationCodeManager.markExchanged(code) } returns false
            coJustRun { grantManager.revokeGrant(grant, GrantRevokedBy.AUTHORIZATION_CODE_REPLAYED, null) }

            val exception = assertThrows<OAuth2Exception> {
                tokenManager.generateTokensForUser(code, grantReadBeforeTheLock(userId), mockk())
            }

            assertEquals(INVALID_GRANT, exception.errorCode)
            assertEquals("code.already_exchanged", exception.detailsId)
            assertEquals("description.oauth2.replay", exception.descriptionId)
            coVerify(exactly = 1) {
                grantManager.revokeGrant(grant, GrantRevokedBy.AUTHORIZATION_CODE_REPLAYED, null)
            }
            coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { refreshTokenGenerator.generateRefreshToken(any(), any(), any(), any()) }
        }

    @Test
    fun `generateTokensForUser - Refuses a code whose grant was revoked before it was exchanged`() = runTest {
        val userId = UUID.randomUUID()
        val code = code()
        stubExchangeableCode(code, userId, mockGrant(revoked = true))
        coEvery { authorizationCodeManager.markExchanged(code) } returns true

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.generateTokensForUser(code, grantReadBeforeTheLock(userId), mockk())
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.revoked", exception.detailsId)
        coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { grantManager.revokeGrant(any(), any(), any()) }
    }

    @Test
    fun `generateTokensForUser - Refuses a code whose grant is past its lifetime`() = runTest {
        val userId = UUID.randomUUID()
        val code = code()
        stubExchangeableCode(code, userId, mockGrant(expired = true))
        coEvery { authorizationCodeManager.markExchanged(code) } returns true

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.generateTokensForUser(code, grantReadBeforeTheLock(userId), mockk())
        }

        assertEquals("grant.expired", exception.detailsId)
        assertEquals("description.grant.expired", exception.descriptionId)
        coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `generateAccessTokenForClient - Issues the access token under the grant it opens`() = runTest {
        val audience = mockk<Audience> { every { id } returns "audience" }
        val client = mockk<Client> {
            every { id } returns "client"
            every { this@mockk.audience } returns audience
        }
        val grant = mockk<Grant> { every { grantType } returns GrantType.CLIENT_CREDENTIALS }
        val accessToken = mockk<EncodedAuthenticationToken>()
        coEvery {
            grantManager.createClientCredentialsGrant("client", "audience", listOf("scope"))
        } returns grant
        stubDoors(grant, client, "jkt")
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.CLIENT_CREDENTIALS, "jkt")
        } returns accessToken

        assertSame(accessToken, tokenManager.generateAccessTokenForClient(client, listOf("scope"), "jkt"))
    }

    @Test
    fun `refreshToken - Throws invalid_grant if the token does not verify`() = runTest {
        coEvery { jwtManager.decodeAndVerify(any(), any()) } throws InvalidJwtException("jwt.expired")

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.refreshToken(mockk(), "test")
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("jwt.expired", exception.detailsId)
    }

    @Test
    fun `refreshToken - Lets a signing key failure through rather than blaming the grant`() = runTest {
        val keyFailure = BusinessException(
            recoverable = false,
            detailsId = "jwt.invalid_key",
            values = mapOf("name" to REFRESH_KEY),
            recommendedStatus = INTERNAL_SERVER_ERROR
        )
        coEvery { jwtManager.decodeAndVerify(any(), any()) } throws keyFailure

        val exception = assertThrows<BusinessException> {
            tokenManager.refreshToken(mockk(), "test")
        }

        assertSame(keyFailure, exception)
        assertEquals(mapOf("name" to REFRESH_KEY), exception.values)
    }

    @Test
    fun `refreshToken - Throws throws if client id does not match`() = runTest {
        val client = mockk<Client>()
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val grant = mockk<Grant> {
            every { userId } returns UUID.randomUUID()
            every { clientId } returns "non-matching-client-id"
        }
        val refreshToken = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { client.id } returns "test-client"
        coEvery { jwtManager.decodeAndVerify(any(), any()) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.refreshToken(client, "test")
        }

        assertEquals("token.mismatching_client", exception.detailsId)
    }

    @Test
    fun `refreshToken - Refuses a grant opened for another audience than its client now belongs to`() = runTest {
        val client = mockClient("test-client")
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val grant = mockk<Grant> {
            every { userId } returns UUID.randomUUID()
            every { clientId } returns "test-client"
            every { audienceId } returns "former-audience"
        }
        val refreshToken = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.refreshToken(client, "token")
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.mismatching_audience", exception.detailsId)
        assertEquals("description.grant.mismatching_audience", exception.descriptionId)
        assertEquals(
            mapOf(
                "grantAudienceId" to "former-audience",
                "clientId" to "test-client",
                "clientAudienceId" to "test-audience"
            ),
            exception.values
        )
        coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refreshToken - Reads the token again under the lock over its account`() = runTest {
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "$userId", keyId = null)
        val grant = mockk<Grant> { every { this@mockk.userId } returns userId }
        val refreshToken = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }
        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery {
            tokenManager.getAuthenticationToken(decodedToken)
        } returns refreshToken andThenThrows oauth2ExceptionOf(INVALID_GRANT, "grant.revoked")

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.refreshToken(mockk(), "token")
        }

        assertEquals("grant.revoked", exception.detailsId)
        coVerifyOrder {
            tokenManager.getAuthenticationToken(decodedToken)
            objectLockRepository.lock(LockKey.User(userId).stripe)
            tokenManager.getAuthenticationToken(decodedToken)
        }
    }

    @Test
    fun `refreshToken - Generates both tokens under the grant if refresh should be refreshed`() = runTest {
        val userId = UUID.randomUUID()
        val client = mockClient("test-client")
        val audience = client.audience
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", userId)
        val grant = refreshToken.grant
        val accessToken = mockk<EncodedAuthenticationToken>()
        val refreshedRefreshToken = mockk<EncodedAuthenticationToken>()

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        coJustRun { userManager.checkPromoted(userId) }
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, "test-audience") } returns mockk()
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.REFRESH_TOKEN, null)
        } returns accessToken
        every { tokenManager.shouldRefreshToken(refreshToken, accessToken) } returns true
        coEvery {
            refreshTokenGenerator.generateRefreshToken(grant, audience, GrantType.REFRESH_TOKEN, null)
        } returns refreshedRefreshToken
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns null

        val tokens = tokenManager.refreshToken(client, "token")

        assertSame(accessToken, tokens.accessToken)
        assertSame(refreshedRefreshToken, tokens.refreshToken)
    }

    @Test
    fun `refreshToken - Generates only access token`() = runTest {
        val userId = UUID.randomUUID()
        val client = mockClient("test-client")
        val audience = client.audience
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", userId)
        val grant = refreshToken.grant
        val accessToken = mockk<EncodedAuthenticationToken>()

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        coJustRun { userManager.checkPromoted(userId) }
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, "test-audience") } returns mockk()
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.REFRESH_TOKEN, null)
        } returns accessToken
        every { tokenManager.shouldRefreshToken(refreshToken, accessToken) } returns false
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns null

        val tokens = tokenManager.refreshToken(client, "token")

        assertSame(accessToken, tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertNull(tokens.idToken)
    }

    @Test
    fun `refreshToken - Binds the new tokens to the key of the proof where the grant had none`() = runTest {
        val client = mockClient("test-client")
        val audience = client.audience
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", null)
        val grant = refreshToken.grant
        val accessToken = mockk<EncodedAuthenticationToken>()

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        stubDoors(grant, client, "jkt")
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.REFRESH_TOKEN, "jkt")
        } returns accessToken
        every { tokenManager.shouldRefreshToken(refreshToken, accessToken) } returns false
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns null

        val tokens = tokenManager.refreshToken(client, "token", dpopJkt = "jkt")

        assertSame(accessToken, tokens.accessToken)
    }

    @Test
    fun `refreshToken - Throws INVALID_GRANT when consent is revoked`() = runTest {
        val userId = UUID.randomUUID()
        val client = mockClient("test-client")
        val audience = client.audience
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", userId)

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        coJustRun { userManager.checkPromoted(userId) }
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, "test-audience") } returns null

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.refreshToken(client, "token")
        }
        assertEquals("grant.consent_revoked", exception.detailsId)
        coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refreshToken - Skips consent check for client_credentials grants`() = runTest {
        val client = mockClient("test-client")
        val audience = client.audience
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", null)
        val grant = refreshToken.grant
        val accessToken = mockk<EncodedAuthenticationToken>()

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.REFRESH_TOKEN, null)
        } returns accessToken
        every { tokenManager.shouldRefreshToken(refreshToken, accessToken) } returns false
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns null

        val tokens = tokenManager.refreshToken(client, "token")

        assertSame(accessToken, tokens.accessToken)
        coVerify(exactly = 0) { consentManager.findActiveConsentByAudienceOrNull(any(), any()) }
    }

    @Test
    fun `refreshToken - Reissues the identity beside the access token`() = runTest {
        val userId = UUID.randomUUID()
        val client = mockClient("test-client")
        val audience = client.audience
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", userId)
        val grant = refreshToken.grant
        val accessToken = mockk<EncodedAuthenticationToken>()
        val idToken = mockk<EncodedAuthenticationToken>()

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        coJustRun { userManager.checkPromoted(userId) }
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, "test-audience") } returns mockk()
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.REFRESH_TOKEN, null)
        } returns accessToken
        every { tokenManager.shouldRefreshToken(refreshToken, accessToken) } returns false
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns idToken

        val tokens = tokenManager.refreshToken(client, "token")

        assertSame(idToken, tokens.idToken)
    }

    @Test
    fun `shouldRefreshToken - False if refresh has no expiration`() {
        val refreshToken = mockk<AuthenticationToken>()
        every { refreshToken.expirationDate } returns null
        assertFalse(tokenManager.shouldRefreshToken(refreshToken, mockk()))
    }

    @Test
    fun `shouldRefreshToken - True if refresh expiration is before access expiration`() {
        val refreshToken = mockk<AuthenticationToken>()
        val accessToken = mockk<EncodedAuthenticationToken>()

        every { refreshToken.expirationDate } returns now().minusDays(1)
        every { accessToken.expirationDate } returns now()

        assertTrue(tokenManager.shouldRefreshToken(refreshToken, accessToken))
    }

    @Test
    fun `shouldRefreshToken - False if refresh expiration is after access expiration`() {
        val refreshToken = mockk<AuthenticationToken>()
        val accessToken = mockk<EncodedAuthenticationToken>()

        every { refreshToken.expirationDate } returns now()
        every { accessToken.expirationDate } returns now().minusDays(1)

        assertFalse(tokenManager.shouldRefreshToken(refreshToken, accessToken))
    }

    @Test
    fun `getAuthenticationToken - Throws if token id is not a valid UUID`() = runTest {
        val decodedToken = DecodedJwt(id = "not-a-uuid", subject = null, keyId = null)
        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("token.invalid_token_id", exception.detailsId)
    }

    @Test
    fun `getAuthenticationToken - Throws if token is missing from database`() = runTest {
        val tokenId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = null, keyId = null)

        coEvery { tokenManager.findById(tokenId) } returns null

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("token.invalid_token_id", exception.detailsId)
    }

    @Test
    fun `getAuthenticationToken - Throws if the grant of the token is revoked`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = userId.toString(), keyId = null)
        val grant = mockk<Grant> {
            every { this@mockk.userId } returns userId
            every { revoked } returns true
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("grant.revoked", exception.detailsId)
        coVerify(exactly = 0) { actorGrantValidator.validateActorGrant(any()) }
    }

    @Test
    fun `getAuthenticationToken - Throws if token subject does not match the user of its grant`() = runTest {
        val tokenId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = "not-the-same", keyId = null)
        val grant = mockk<Grant> {
            every { userId } returns UUID.randomUUID()
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("token.invalid_token_id", exception.detailsId)
    }

    @Test
    fun `getAuthenticationToken - Throws if the grant of the token is past its lifetime`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = userId.toString(), keyId = null)
        val grant = mockk<Grant> {
            every { this@mockk.userId } returns userId
            every { revoked } returns false
            every { expired } returns true
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.expired", exception.detailsId)
        assertEquals("description.grant.expired", exception.descriptionId)
    }

    @Test
    fun `getAuthenticationToken - Return token info from database if validated`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = userId.toString(), keyId = null)
        val grant = mockk<Grant> {
            every { this@mockk.userId } returns userId
            every { revoked } returns false
            every { expired } returns false
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token
        coJustRun { actorGrantValidator.validateActorGrant(grant) }

        assertSame(token, tokenManager.getAuthenticationToken(decodedToken))
    }

    @Test
    fun `getAuthenticationToken - Takes the client as the subject of a grant naming no user`() = runTest {
        val tokenId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = "client", keyId = null)
        val grant = mockk<Grant> {
            every { userId } returns null
            every { clientId } returns "client"
            every { revoked } returns false
            every { expired } returns false
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token
        coJustRun { actorGrantValidator.validateActorGrant(grant) }

        assertSame(token, tokenManager.getAuthenticationToken(decodedToken))
    }

    @Test
    fun `getAuthenticationToken - Throws if the actor grant is no longer live`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = userId.toString(), keyId = null)
        val grant = mockk<Grant> {
            every { this@mockk.userId } returns userId
            every { revoked } returns false
            every { expired } returns false
        }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { tokenManager.findById(tokenId) } returns token
        coEvery {
            actorGrantValidator.validateActorGrant(grant)
        } throws oauth2ExceptionOf(INVALID_GRANT, "grant.actor_revoked")

        val exception = assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
        assertEquals("grant.actor_revoked", exception.detailsId)
    }

    @Test
    fun `revokeTokenByEncodedToken - Does not throw when token cannot be decoded`() = runTest {
        val client = mockk<Client>()
        every { jwtManager.getKeyIdOrNull("invalid") } returns null

        tokenManager.revokeTokenByEncodedToken(client, "invalid")

        coVerify(exactly = 0) { grantManager.revokeGrant(any(), any(), any()) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Does not revoke when token belongs to another client`() = runTest {
        val tokenId = UUID.randomUUID()
        val client = mockk<Client> { every { id } returns "client-a" }
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "client-b" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "token") } returns decodedToken
        coEvery { tokenManager.findById(tokenId) } returns token

        tokenManager.revokeTokenByEncodedToken(client, "token")

        coVerify(exactly = 0) { grantManager.revokeGrant(any(), any(), any()) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Revokes the grant of an access token`() = runTest {
        val tokenId = UUID.randomUUID()
        val client = mockk<Client> { every { id } returns "client-a" }
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "client-a" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "token") } returns decodedToken
        coEvery { tokenManager.findById(tokenId) } returns token
        coJustRun { grantManager.revokeGrant(grant, GrantRevokedBy.CLIENT, null) }

        tokenManager.revokeTokenByEncodedToken(client, "token")

        coVerify(exactly = 1) { grantManager.revokeGrant(grant, GrantRevokedBy.CLIENT, null) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Revokes the grant of a refresh token`() = runTest {
        val tokenId = UUID.randomUUID()
        val client = mockk<Client> { every { id } returns "client-a" }
        val decodedToken = DecodedJwt(id = tokenId.toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "client-a" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("token") } returns REFRESH_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.findById(tokenId) } returns token
        coJustRun { grantManager.revokeGrant(grant, GrantRevokedBy.CLIENT, null) }

        tokenManager.revokeTokenByEncodedToken(client, "token")

        coVerify(exactly = 0) { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, any()) }
        coVerify(exactly = 1) { grantManager.revokeGrant(grant, GrantRevokedBy.CLIENT, null) }
    }

    @Test
    fun `introspectToken - Returns token for active access token`() = runTest {
        val client = mockk<Client> { every { id } returns "test-client" }
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "test-client" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertSame(token, result)
    }

    @Test
    fun `introspectToken - Returns token for active refresh token`() = runTest {
        val client = mockk<Client> { every { id } returns "test-client" }
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "test-client" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns REFRESH_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertSame(token, result)
    }

    @Test
    fun `introspectToken - Returns null for a token of another type than the one asked for`() = runTest {
        val client = mockk<Client>()
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val token = mockk<AuthenticationToken> { every { type } returns REFRESH }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns REFRESH_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = ACCESS)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null for malformed token`() = runTest {
        val client = mockk<Client>()
        every { jwtManager.getKeyIdOrNull("malformed") } returns null

        val result = tokenManager.introspectToken(client, "malformed", type = null)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null for expired token`() = runTest {
        val client = mockk<Client>()
        every { jwtManager.getKeyIdOrNull("expired-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "expired-token") } returns null

        val result = tokenManager.introspectToken(client, "expired-token", type = null)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null for a token the server refuses`() = runTest {
        val client = mockk<Client>()
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "encoded-token") } returns decodedJwt
        coEvery {
            tokenManager.getAuthenticationToken(decodedJwt)
        } throws oauth2ExceptionOf(INVALID_GRANT, "grant.revoked")

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertNull(result)
    }

    @Test
    fun `introspectToken - Returns null when client does not match`() = runTest {
        val client = mockk<Client> { every { id } returns "other-client" }
        val decodedJwt = DecodedJwt(id = UUID.randomUUID().toString(), subject = null, keyId = null)
        val grant = mockk<Grant> { every { clientId } returns "test-client" }
        val token = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { jwtManager.getKeyIdOrNull("encoded-token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "encoded-token") } returns decodedJwt
        coEvery { tokenManager.getAuthenticationToken(decodedJwt) } returns token

        val result = tokenManager.introspectToken(client, "encoded-token", type = null)

        assertNull(result)
    }

    @Test
    fun `decodeAndVerifyAccessOrRefreshTokenOrNull - Returns null for a token naming another key`() = runTest {
        every { jwtManager.getKeyIdOrNull("id-token") } returns PUBLIC_KEY

        val result = tokenManager.decodeAndVerifyAccessOrRefreshTokenOrNull("id-token")

        assertNull(result)
        coVerify(exactly = 0) { jwtManager.decodeAndVerifyOrNull(any(), any()) }
    }
}
