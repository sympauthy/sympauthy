package com.sympauthy.business.manager.auth.oauth2

import com.auth0.jwt.interfaces.DecodedJWT
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.exception.localizedExceptionOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime.now
import java.util.*

@Suppress("unused")
@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
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
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var tokenMapper: AuthenticationTokenMapper

    @SpyK
    @InjectMockKs
    lateinit var tokenManager: TokenManager

    @Test
    fun `generateTokens - Throws if session is expired`() = runTest {
        val attempt = mockk<CompletedAuthorizeAttempt>()
        every { attempt.expired } returns true
        assertThrows<OAuth2Exception> {
            tokenManager.generateTokens(attempt)
        }
    }

    @Test
    fun `generateTokens - Generate all tokens`() = runTest {
        val userId = UUID.randomUUID()
        val authorizedAttempt = mockk<CompletedAuthorizeAttempt>()
        val accessToken = mockk<EncodedAuthenticationToken>()
        val refreshToken = mockk<EncodedAuthenticationToken>()
        val idToken = mockk<EncodedAuthenticationToken>()

        every { authorizedAttempt.expired } returns false
        every { authorizedAttempt.userId } returns userId
        coEvery { accessTokenGenerator.generateAccessToken(authorizedAttempt, userId) } returns accessToken
        coEvery { refreshTokenGenerator.generateRefreshToken(authorizedAttempt, userId) } returns refreshToken
        coEvery { idTokenGenerator.generateIdToken(authorizedAttempt, userId, accessToken) } returns idToken

        val result = tokenManager.generateTokens(authorizedAttempt)

        assertSame(accessToken, result.accessToken)
        assertSame(refreshToken, result.refreshToken)
    }

    @Test
    fun `refreshToken - Throws if token is invalid`() = runTest {
        val exception = localizedExceptionOf("test")
        coEvery { jwtManager.decodeAndVerify(any(), any()) } throws exception
        assertThrows<OAuth2Exception> {
            tokenManager.refreshToken(mockk(), "test")
        }
    }

    @Test
    fun `refreshToken - Throws throws if client id does not match`() = runTest {
        val client = mockk<Client>()
        val decodedToken = mockk<DecodedJWT>()
        val refreshToken = mockk<AuthenticationToken>()

        every { client.id } returns "test-client"
        coEvery { jwtManager.decodeAndVerify(any(), any()) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        every { refreshToken.clientId } returns "non-matching-client-id"

        assertThrows<OAuth2Exception> {
            tokenManager.refreshToken(client, "test")
        }
    }

    @Test
    fun `refreshToken - Generates both tokens if refresh should be refreshed`() = runTest {
        val clientId = "test-client"
        val encodedRefreshToken = "token"
        val client = mockk<Client>()
        val decodedToken = mockk<DecodedJWT>()
        val refreshToken = mockk<AuthenticationToken>()
        val accessToken = mockk<EncodedAuthenticationToken>()
        val refreshedRefreshToken = mockk<EncodedAuthenticationToken>()

        every { client.id } returns clientId
        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, encodedRefreshToken) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        every { refreshToken.clientId } returns clientId
        coEvery { accessTokenGenerator.generateAccessToken(refreshToken) } returns accessToken
        every { tokenManager.shouldRefreshToken(refreshToken, accessToken) } returns true
        coEvery { refreshTokenGenerator.generateRefreshToken(refreshToken) } returns refreshedRefreshToken

        val tokens = tokenManager.refreshToken(client, encodedRefreshToken)

        assertEquals(2, tokens.count())
        assertSame(accessToken, tokens[0])
        assertSame(refreshedRefreshToken, tokens[1])
    }

    @Test
    fun `refreshToken - Generates only access token`() = runTest {
        val clientId = "test-client"
        val encodedRefreshToken = "token"
        val client = mockk<Client>()
        val decodedToken = mockk<DecodedJWT>()
        val refreshToken = mockk<AuthenticationToken>()
        val accessToken = mockk<EncodedAuthenticationToken>()

        every { client.id } returns clientId
        coEvery { jwtManager.decodeAndVerify(REFRESH_KEY, encodedRefreshToken) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns refreshToken
        every { refreshToken.clientId } returns clientId
        coEvery { accessTokenGenerator.generateAccessToken(refreshToken) } returns accessToken
        every { tokenManager.shouldRefreshToken(refreshToken, accessToken) } returns false

        val tokens = tokenManager.refreshToken(client, encodedRefreshToken)

        assertEquals(1, tokens.count())
        assertSame(accessToken, tokens[0])
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
        val decodedToken = mockk<DecodedJWT>()
        every { decodedToken.id } returns "not-a-uuid"
        assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
    }

    @Test
    fun `getAuthenticationToken - Throws if token is missing from database`() = runTest {
        val tokenId = UUID.randomUUID()
        val decodedToken = mockk<DecodedJWT>()

        every { decodedToken.id } returns tokenId.toString()
        coEvery { tokenManager.findById(tokenId) } returns null

        assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
    }

    @Test
    fun `getAuthenticationToken - Throws if token is revoked`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = mockk<DecodedJWT> {
            every { id } returns tokenId.toString()
        }
        val token = mockk<AuthenticationToken> {
            every { this@mockk.userId } returns userId
        }

        coEvery { tokenManager.findById(tokenId) } returns token
        every { token.revoked } returns true

        assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
    }

    @Test
    fun `getAuthenticationToken - Throws if token subject does not match user in database`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = mockk<DecodedJWT>()
        val token = mockk<AuthenticationToken>()

        every { decodedToken.id } returns tokenId.toString()
        every { decodedToken.subject } returns "not-the-same"
        coEvery { tokenManager.findById(tokenId) } returns token
        every { token.userId } returns userId
        every { token.revoked } returns false

        assertThrows<OAuth2Exception> {
            tokenManager.getAuthenticationToken(decodedToken)
        }
    }

    @Test
    fun `getAuthenticationToken - Return token info from database if validated`() = runTest {
        val tokenId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val decodedToken = mockk<DecodedJWT>()
        val token = mockk<AuthenticationToken>()

        every { decodedToken.id } returns tokenId.toString()
        every { decodedToken.subject } returns userId.toString()
        coEvery { tokenManager.findById(tokenId) } returns token
        every { token.userId } returns userId
        every { token.revoked } returns false

        assertSame(token, tokenManager.getAuthenticationToken(decodedToken))
    }

    @Test
    fun `revokeTokenByEncodedToken - Does not throw when token cannot be decoded`() = runTest {
        val client = mockk<Client>()
        every { jwtManager.getKeyIdOrNull("invalid") } returns null

        tokenManager.revokeTokenByEncodedToken(client, "invalid", null)
    }

    @Test
    fun `revokeTokenByEncodedToken - Does not revoke when token belongs to another client`() = runTest {
        val tokenId = UUID.randomUUID()
        val client = mockk<Client>()
        val decodedToken = mockk<DecodedJWT>()
        val token = mockk<AuthenticationToken>()

        every { client.id } returns "client-a"
        every { jwtManager.getKeyIdOrNull("token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "token") } returns decodedToken
        every { decodedToken.id } returns tokenId.toString()
        coEvery { tokenManager.findById(tokenId) } returns token
        every { token.clientId } returns "client-b"

        tokenManager.revokeTokenByEncodedToken(client, "token", null)

        coVerify(exactly = 0) { tokenRepository.updateRevokedById(any(), any()) }
        coVerify(exactly = 0) { tokenRepository.updateRevokedByAuthorizeAttemptId(any(), any()) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Revokes access token`() = runTest {
        val clientId = "client-a"
        val tokenId = UUID.randomUUID()
        val client = mockk<Client>()
        val decodedToken = mockk<DecodedJWT>()
        val token = mockk<AuthenticationToken>()

        every { client.id } returns clientId
        every { jwtManager.getKeyIdOrNull("token") } returns ACCESS_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "token") } returns decodedToken
        every { decodedToken.id } returns tokenId.toString()
        coEvery { tokenManager.findById(tokenId) } returns token
        every { token.clientId } returns clientId
        every { token.type } returns ACCESS
        every { token.id } returns tokenId
        coEvery { tokenRepository.updateRevokedById(tokenId, true) } returns Unit

        tokenManager.revokeTokenByEncodedToken(client, "token", null)

        coVerify(exactly = 1) { tokenRepository.updateRevokedById(tokenId, true) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Cascades revocation for refresh token when hint is refresh_token`() = runTest {
        val clientId = "client-a"
        val tokenId = UUID.randomUUID()
        val attemptId = UUID.randomUUID()
        val client = mockk<Client>()
        val decodedToken = mockk<DecodedJWT>()
        val token = mockk<AuthenticationToken>()

        every { client.id } returns clientId
        coEvery { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, "token") } returns decodedToken
        every { decodedToken.id } returns tokenId.toString()
        coEvery { tokenManager.findById(tokenId) } returns token
        every { token.clientId } returns clientId
        every { token.type } returns REFRESH
        every { token.authorizeAttemptId } returns attemptId
        coEvery { tokenRepository.updateRevokedByAuthorizeAttemptId(attemptId, true) } returns Unit

        tokenManager.revokeTokenByEncodedToken(client, "token", "refresh_token")

        coVerify(exactly = 0) { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, any()) }
        coVerify(exactly = 1) { tokenRepository.updateRevokedByAuthorizeAttemptId(attemptId, true) }
        coVerify(exactly = 0) { tokenRepository.updateRevokedById(any(), any()) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Uses only public key when hint is access_token`() = runTest {
        val clientId = "client-a"
        val tokenId = UUID.randomUUID()
        val client = mockk<Client>()
        val decodedToken = mockk<DecodedJWT>()
        val token = mockk<AuthenticationToken>()

        every { client.id } returns clientId
        coEvery { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, "token") } returns decodedToken
        every { decodedToken.id } returns tokenId.toString()
        coEvery { tokenManager.findById(tokenId) } returns token
        every { token.clientId } returns clientId
        every { token.type } returns ACCESS
        every { token.id } returns tokenId
        coEvery { tokenRepository.updateRevokedById(tokenId, true) } returns Unit

        tokenManager.revokeTokenByEncodedToken(client, "token", "access_token")

        coVerify(exactly = 0) { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, any()) }
        coVerify(exactly = 1) { tokenRepository.updateRevokedById(tokenId, true) }
    }

    @Test
    fun `revokeTokenByEncodedToken - Uses refresh key when no hint and kid is REFRESH_KEY`() = runTest {
        val clientId = "client-a"
        val tokenId = UUID.randomUUID()
        val attemptId = UUID.randomUUID()
        val client = mockk<Client>()
        val decodedToken = mockk<DecodedJWT>()
        val token = mockk<AuthenticationToken>()

        every { client.id } returns clientId
        every { jwtManager.getKeyIdOrNull("token") } returns REFRESH_KEY
        coEvery { jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, "token") } returns decodedToken
        every { decodedToken.id } returns tokenId.toString()
        coEvery { tokenManager.findById(tokenId) } returns token
        every { token.clientId } returns clientId
        every { token.type } returns REFRESH
        every { token.authorizeAttemptId } returns attemptId
        coEvery { tokenRepository.updateRevokedByAuthorizeAttemptId(attemptId, true) } returns Unit

        tokenManager.revokeTokenByEncodedToken(client, "token", null)

        coVerify(exactly = 0) { jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, any()) }
        coVerify(exactly = 1) { tokenRepository.updateRevokedByAuthorizeAttemptId(attemptId, true) }
    }
}
