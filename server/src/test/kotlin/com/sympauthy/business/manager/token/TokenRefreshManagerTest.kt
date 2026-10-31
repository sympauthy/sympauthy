package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.data.repository.ObjectLockRepository
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
import com.sympauthy.monitoring.telemetry.LockMetrics
import com.sympauthy.monitoring.telemetry.TokenMetrics
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.LocalDateTime.now
import java.util.*

@ExtendWith(MockKExtension::class)
class TokenRefreshManagerTest {

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var tokenManager: TokenManager

    private val userManager = mockk<UserManager>()

    /**
     * The real manager behind a spy rather than a mock: whether a grant is live, opened for the client
     * presenting it and opened for a finished account are functions of the grant, the client and the account
     * alone, so every scenario runs them instead of stubbing an answer to them.
     */
    @SpyK
    var grantManager = GrantManager(mockk(), mockk(), mockk(), userManager, mockk(), TokenMetrics(OpenTelemetry.noop()))

    @MockK
    lateinit var consentManager: ConsentManager

    /**
     * The real manager behind a spy rather than a mock: the binding rule a refresh asks it is a function of the
     * token alone, so every refresh scenario runs it instead of stubbing an answer to it.
     */
    @SpyK
    var dpopManager = DpopManager(EnabledUrlsConfig(root = URI.create("https://auth.example.com")))

    private val objectLockRepository = mockk<ObjectLockRepository>(relaxed = true)

    /** The real manager over the stripe table, so a test can say which row was taken before what. */
    var lockManager: LockManager = LockManager(objectLockRepository, HeldStripes(), LockMetrics(OpenTelemetry.noop()))

    @MockK
    lateinit var accessTokenGenerator: AccessTokenGenerator

    @MockK
    lateinit var refreshTokenGenerator: RefreshTokenGenerator

    @MockK
    lateinit var idTokenGenerator: IdTokenGenerator

    @MockK
    lateinit var claimAclManager: ClaimAclManager

    @SpyK
    var customSpanTracer = CustomSpanTracer(OpenTelemetry.noop())

    @SpyK
    @InjectMockKs
    lateinit var manager: TokenRefreshManager

    private fun mockRefreshableToken(clientId: String, userId: UUID?): AuthenticationToken {
        val grant = mockk<Grant> {
            every { this@mockk.clientId } returns clientId
            every { this@mockk.userId } returns userId
            every { audienceId } returns "test-audience"
            every { sessionId } returns null
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

    @Test
    fun `refresh - Throws invalid_grant if the token does not verify`() = runTest {
        coEvery { jwtManager.decodeAndVerify(any(), any()) } throws InvalidJwtException("jwt.expired")

        val exception = assertThrows<OAuth2Exception> {
            manager.refresh(mockk(), TokenRequest.RefreshToken("test"))
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("jwt.expired", exception.detailsId)
    }

    @Test
    fun `refresh - Lets a signing key failure through rather than blaming the grant`() = runTest {
        val keyFailure = BusinessException(
            recoverable = false,
            detailsId = "jwt.invalid_key",
            values = mapOf("name" to REFRESH_KEY),
            recommendedStatus = INTERNAL_SERVER_ERROR
        )
        coEvery { jwtManager.decodeAndVerify(any(), any()) } throws keyFailure

        val exception = assertThrows<BusinessException> {
            manager.refresh(mockk(), TokenRequest.RefreshToken("test"))
        }

        assertSame(keyFailure, exception)
        assertEquals(mapOf("name" to REFRESH_KEY), exception.values)
    }

    @Test
    fun `refresh - Throws throws if client id does not match`() = runTest {
        val client = mockk<Client>()
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val grant = mockk<Grant> {
            every { userId } returns UUID.randomUUID()
            every { clientId } returns "non-matching-client-id"
            every { sessionId } returns null
        }
        val refreshToken = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        every { client.id } returns "test-client"
        coEvery { jwtManager.decodeAndVerify(any(), any()) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken

        val exception = assertThrows<OAuth2Exception> {
            manager.refresh(client, TokenRequest.RefreshToken("test"))
        }

        assertEquals("token.mismatching_client", exception.detailsId)
    }

    @Test
    fun `refresh - Refuses a grant opened for another audience than its client now belongs to`() = runTest {
        val client = mockClient("test-client")
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val grant = mockk<Grant> {
            every { userId } returns UUID.randomUUID()
            every { clientId } returns "test-client"
            every { sessionId } returns null
            every { audienceId } returns "former-audience"
        }
        val refreshToken = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken

        val exception = assertThrows<OAuth2Exception> {
            manager.refresh(client, TokenRequest.RefreshToken("token"))
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
    fun `refresh - Reads the token again under the lock over its account`() = runTest {
        val userId = UUID.randomUUID()
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "$userId", keyId = null)
        val grant = mockk<Grant> { every { this@mockk.userId } returns userId }
        val refreshToken = mockk<AuthenticationToken> { every { this@mockk.grant } returns grant }
        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery {
            tokenManager.getAuthenticationToken(decodedToken)
        } returns refreshToken andThenThrows oauth2ExceptionOf(INVALID_GRANT, "grant.revoked")

        val exception = assertThrows<OAuth2Exception> {
            manager.refresh(mockk(), TokenRequest.RefreshToken("token"))
        }

        assertEquals("grant.revoked", exception.detailsId)
        coVerifyOrder {
            tokenManager.getAuthenticationToken(decodedToken)
            objectLockRepository.lock(LockKey.User(userId).stripe)
            tokenManager.getAuthenticationToken(decodedToken)
        }
    }

    @Test
    fun `refresh - Generates both tokens under the grant if refresh should be refreshed`() = runTest {
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
        coEvery { userManager.findByIdOrNull(userId) } returns mockk()
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, "test-audience") } returns mockk()
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.REFRESH_TOKEN, null)
        } returns accessToken
        every { manager.shouldRefreshToken(refreshToken, accessToken) } returns true
        coEvery {
            refreshTokenGenerator.generateRefreshToken(grant, audience, GrantType.REFRESH_TOKEN, null)
        } returns refreshedRefreshToken
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns null

        val tokens = manager.refresh(client, TokenRequest.RefreshToken("token"))

        assertSame(accessToken, tokens.accessToken)
        assertSame(refreshedRefreshToken, tokens.refreshToken)
    }

    @Test
    fun `refresh - Generates only access token`() = runTest {
        val userId = UUID.randomUUID()
        val client = mockClient("test-client")
        val audience = client.audience
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", userId)
        val grant = refreshToken.grant
        val accessToken = mockk<EncodedAuthenticationToken>()

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        coEvery { userManager.findByIdOrNull(userId) } returns mockk()
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, "test-audience") } returns mockk()
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.REFRESH_TOKEN, null)
        } returns accessToken
        every { manager.shouldRefreshToken(refreshToken, accessToken) } returns false
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns null

        val tokens = manager.refresh(client, TokenRequest.RefreshToken("token"))

        assertSame(accessToken, tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertNull(tokens.idToken)
    }

    @Test
    fun `refresh - Binds the new tokens to the key of the proof where the grant had none`() = runTest {
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
        every { manager.shouldRefreshToken(refreshToken, accessToken) } returns false
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns null

        val tokens = manager.refresh(client, TokenRequest.RefreshToken("token"), dpopJkt = "jkt")

        assertSame(accessToken, tokens.accessToken)
    }

    @Test
    fun `refresh - Throws INVALID_GRANT when consent is revoked`() = runTest {
        val userId = UUID.randomUUID()
        val client = mockClient("test-client")
        val audience = client.audience
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", userId)

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        coEvery { userManager.findByIdOrNull(userId) } returns mockk()
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, "test-audience") } returns null

        val exception = assertThrows<OAuth2Exception> {
            manager.refresh(client, TokenRequest.RefreshToken("token"))
        }
        assertEquals("grant.consent_revoked", exception.detailsId)
        coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refresh - Skips consent check for client_credentials grants`() = runTest {
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
        every { manager.shouldRefreshToken(refreshToken, accessToken) } returns false
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns null

        val tokens = manager.refresh(client, TokenRequest.RefreshToken("token"))

        assertSame(accessToken, tokens.accessToken)
        coVerify(exactly = 0) { consentManager.findActiveConsentByAudienceOrNull(any(), any()) }
    }

    @Test
    fun `refresh - Reissues the identity beside the access token`() = runTest {
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
        coEvery { userManager.findByIdOrNull(userId) } returns mockk()
        coEvery { consentManager.findActiveConsentByAudienceOrNull(userId, "test-audience") } returns mockk()
        stubDoors(grant, client, null)
        coEvery {
            accessTokenGenerator.generateAccessToken(grant, audience, doors, GrantType.REFRESH_TOKEN, null)
        } returns accessToken
        every { manager.shouldRefreshToken(refreshToken, accessToken) } returns false
        coEvery {
            idTokenGenerator.generateIdToken(
                grant, match { it.id == "test-audience" }, accessToken, doors, GrantType.REFRESH_TOKEN
            )
        } returns idToken

        val tokens = manager.refresh(client, TokenRequest.RefreshToken("token"))

        assertSame(idToken, tokens.idToken)
    }

    @Test
    fun `shouldRefreshToken - False if refresh has no expiration`() {
        val refreshToken = mockk<AuthenticationToken>()
        every { refreshToken.expirationDate } returns null
        assertFalse(manager.shouldRefreshToken(refreshToken, mockk()))
    }

    @Test
    fun `shouldRefreshToken - True if refresh expiration is before access expiration`() {
        val refreshToken = mockk<AuthenticationToken>()
        val accessToken = mockk<EncodedAuthenticationToken>()

        every { refreshToken.expirationDate } returns now().minusDays(1)
        every { accessToken.expirationDate } returns now()

        assertTrue(manager.shouldRefreshToken(refreshToken, accessToken))
    }

    @Test
    fun `shouldRefreshToken - False if refresh expiration is after access expiration`() {
        val refreshToken = mockk<AuthenticationToken>()
        val accessToken = mockk<EncodedAuthenticationToken>()

        every { refreshToken.expirationDate } returns now()
        every { accessToken.expirationDate } returns now().minusDays(1)

        assertFalse(manager.shouldRefreshToken(refreshToken, accessToken))
    }

    @Test
    fun `refresh - Refuses an account no sign-up finished, and issues nothing`() = runTest {
        val userId = UUID.randomUUID()
        val client = mockClient("test-client")
        val decodedToken = DecodedJwt(id = UUID.randomUUID().toString(), subject = "sub", keyId = null)
        val refreshToken = mockRefreshableToken("test-client", userId)

        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, "token") } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<OAuth2Exception> {
            manager.refresh(client, TokenRequest.RefreshToken("token"))
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("grant.invalid_user", exception.detailsId)
        coVerify(exactly = 0) { consentManager.findActiveConsentByAudienceOrNull(any(), any()) }
        coVerify(exactly = 0) { accessTokenGenerator.generateAccessToken(any(), any(), any(), any(), any()) }
    }

}
