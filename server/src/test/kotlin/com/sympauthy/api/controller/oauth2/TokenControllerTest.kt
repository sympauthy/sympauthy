package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.oauth2.*
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_GRANT_TYPE
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class TokenControllerTest {

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var authorizeFlowManager: AuthorizationFlowManager

    @MockK
    lateinit var tokenManager: TokenManager

    @MockK
    lateinit var accessTokenGenerator: AccessTokenGenerator

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var clientAuthenticationUtil: ClientAuthenticationUtil

    @MockK
    lateinit var pkceManager: PkceManager

    @InjectMockKs
    lateinit var controller: TokenController

    private fun mockRequestWithoutAuth(): HttpRequest<*> {
        val headers = mockk<HttpHeaders> {
            every { authorization } returns Optional.empty()
        }
        return mockk {
            every { this@mockk.headers } returns headers
        }
    }

    private fun mockClient(id: String = "test-client"): Client {
        return mockk { every { this@mockk.id } returns id }
    }

    private fun mockEncodedToken(
        token: String = "encoded-token",
        scopes: List<String> = emptyList(),
        expirationDate: LocalDateTime? = null,
        type: AuthenticationTokenType = ACCESS
    ): EncodedAuthenticationToken {
        return mockk {
            every { this@mockk.token } returns token
            every { this@mockk.scopes } returns scopes
            every { this@mockk.expirationDate } returns expirationDate
            every { this@mockk.type } returns type
        }
    }

    // --- getTokens routing tests ---

    @Test
    fun `getTokens - client_credentials uses resolveClient, not resolveClientForAuthorizationCodeGrant`() = runTest {
        val request = mockRequestWithoutAuth()
        coEvery {
            clientAuthenticationUtil.resolveClient(request, any(), any())
        } throws oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")

        assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "client_credentials",
                code = null,
                redirectUri = null,
                refreshToken = null,
                scope = null,
                clientId = "any-client",
                clientSecret = null,
                codeVerifier = null
            )
        }

        coVerify(exactly = 1) { clientAuthenticationUtil.resolveClient(request, any(), any()) }
        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClientForAuthorizationCodeGrant(any(), any(), any()) }
    }

    @Test
    fun `getTokens - refresh_token uses resolveClient, not resolveClientForAuthorizationCodeGrant`() = runTest {
        val request = mockRequestWithoutAuth()
        coEvery {
            clientAuthenticationUtil.resolveClient(request, any(), any())
        } throws oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")

        assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "refresh_token",
                code = null,
                redirectUri = null,
                refreshToken = "some-token",
                scope = null,
                clientId = "any-client",
                clientSecret = null,
                codeVerifier = null
            )
        }

        coVerify(exactly = 1) { clientAuthenticationUtil.resolveClient(request, any(), any()) }
        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClientForAuthorizationCodeGrant(any(), any(), any()) }
    }

    @Test
    fun `getTokens - authorization_code uses resolveClientForAuthorizationCodeGrant, not resolveClient`() = runTest {
        val request = mockRequestWithoutAuth()
        coEvery {
            clientAuthenticationUtil.resolveClientForAuthorizationCodeGrant(request, any(), any())
        } throws oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")

        assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "authorization_code",
                code = "some-code",
                redirectUri = null,
                refreshToken = null,
                scope = null,
                clientId = "any-client",
                clientSecret = null,
                codeVerifier = null
            )
        }

        coVerify(exactly = 1) { clientAuthenticationUtil.resolveClientForAuthorizationCodeGrant(request, any(), any()) }
        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClient(any(), any(), any()) }
    }

    @Test
    fun `getTokens - Throws unsupported_grant_type for unknown grant`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = mockRequestWithoutAuth(),
                grantType = "unknown",
                code = null,
                redirectUri = null,
                refreshToken = null,
                scope = null,
                clientId = null,
                clientSecret = null,
                codeVerifier = null
            )
        }
        assertEquals(UNSUPPORTED_GRANT_TYPE, exception.errorCode)
    }

    // --- getTokensUsingAuthorizationCode tests ---

    @Test
    fun `getTokensUsingAuthorizationCode - Throws when code is missing`() = runTest {
        val request = mockRequestWithoutAuth()
        coEvery {
            clientAuthenticationUtil.resolveClientForAuthorizationCodeGrant(request, any(), any())
        } returns mockClient()

        val exception = assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "authorization_code",
                code = null,
                redirectUri = null,
                refreshToken = null,
                scope = null,
                clientId = "client",
                clientSecret = "secret",
                codeVerifier = null
            )
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
    }

    @Test
    fun `getTokensUsingAuthorizationCode - Throws when redirect_uri does not match`() = runTest {
        val request = mockRequestWithoutAuth()
        val completedAttempt = mockk<CompletedAuthorizeAttempt> {
            every { redirectUri } returns "https://example.com/callback"
            every { codeChallenge } returns null
            every { codeChallengeMethod } returns null
        }
        coEvery {
            clientAuthenticationUtil.resolveClientForAuthorizationCodeGrant(request, any(), any())
        } returns mockClient()
        coEvery { authorizeAttemptManager.findByCodeOrNull("the-code") } returns completedAttempt
        coEvery { authorizeFlowManager.checkCanIssueToken(completedAttempt) } returns completedAttempt

        val exception = assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "authorization_code",
                code = "the-code",
                redirectUri = "https://other.com/callback",
                refreshToken = null,
                scope = null,
                clientId = "client",
                clientSecret = "secret",
                codeVerifier = null
            )
        }
        assertEquals("token.non_matching_redirect_uri", exception.detailsId)
    }

    @Test
    fun `getTokensUsingAuthorizationCode - Throws when PKCE verification fails`() = runTest {
        val request = mockRequestWithoutAuth()
        val completedAttempt = mockk<CompletedAuthorizeAttempt> {
            every { redirectUri } returns "https://example.com/callback"
            every { codeChallenge } returns "stored-challenge"
            every { codeChallengeMethod } returns CodeChallengeMethod.S256
        }
        coEvery {
            clientAuthenticationUtil.resolveClientForAuthorizationCodeGrant(request, any(), any())
        } returns mockClient()
        coEvery { authorizeAttemptManager.findByCodeOrNull("the-code") } returns completedAttempt
        coEvery { authorizeFlowManager.checkCanIssueToken(completedAttempt) } returns completedAttempt
        every {
            pkceManager.verifyCodeVerifier("wrong-verifier", "stored-challenge", CodeChallengeMethod.S256)
        } throws businessExceptionOf(detailsId = "token.pkce.invalid_code_verifier")

        val exception = assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "authorization_code",
                code = "the-code",
                redirectUri = "https://example.com/callback",
                refreshToken = null,
                scope = null,
                clientId = "client",
                clientSecret = "secret",
                codeVerifier = "wrong-verifier"
            )
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.pkce.invalid_code_verifier", exception.detailsId)
    }

    @Test
    fun `getTokensUsingAuthorizationCode - Returns tokens on success`() = runTest {
        val request = mockRequestWithoutAuth()
        val accessToken = mockEncodedToken("access-jwt", listOf("openid"))
        val refreshToken = mockEncodedToken("refresh-jwt", type = REFRESH)
        val idToken = mockEncodedToken("id-jwt")
        val completedAttempt = mockk<CompletedAuthorizeAttempt> {
            every { redirectUri } returns "https://example.com/callback"
            every { codeChallenge } returns null
            every { codeChallengeMethod } returns null
        }
        coEvery {
            clientAuthenticationUtil.resolveClientForAuthorizationCodeGrant(request, any(), any())
        } returns mockClient()
        coEvery { authorizeAttemptManager.findByCodeOrNull("the-code") } returns completedAttempt
        coEvery { authorizeFlowManager.checkCanIssueToken(completedAttempt) } returns completedAttempt
        every { pkceManager.verifyCodeVerifier(null, null, null) } just runs
        coEvery { tokenManager.generateTokens(completedAttempt) } returns GenerateTokenResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = idToken
        )

        val result = controller.getTokens(
            request = request,
            grantType = "authorization_code",
            code = "the-code",
            redirectUri = "https://example.com/callback",
            refreshToken = null,
            scope = null,
            clientId = "client",
            clientSecret = "secret",
            codeVerifier = null
        )

        assertEquals("access-jwt", result.accessToken)
        assertEquals("bearer", result.tokenType)
        assertEquals("refresh-jwt", result.refreshToken)
        assertEquals("id-jwt", result.idToken)
    }

    // --- getTokensUsingRefreshToken tests ---

    @Test
    fun `getTokensUsingRefreshToken - Throws when refresh_token is missing`() = runTest {
        val request = mockRequestWithoutAuth()
        val client = mockClient()
        coEvery { clientAuthenticationUtil.resolveClient(request, any(), any()) } returns client

        val exception = assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "refresh_token",
                code = null,
                redirectUri = null,
                refreshToken = null,
                scope = null,
                clientId = "client",
                clientSecret = "secret",
                codeVerifier = null
            )
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
    }

    @Test
    fun `getTokensUsingRefreshToken - Returns tokens with refreshed refresh token`() = runTest {
        val request = mockRequestWithoutAuth()
        val client = mockClient()
        val accessToken = mockEncodedToken("new-access", listOf("openid"), type = ACCESS)
        val newRefreshToken = mockEncodedToken("new-refresh", type = REFRESH)

        coEvery { clientAuthenticationUtil.resolveClient(request, any(), any()) } returns client
        coEvery { tokenManager.refreshToken(client, "old-refresh") } returns listOf(accessToken, newRefreshToken)

        val result = controller.getTokens(
            request = request,
            grantType = "refresh_token",
            code = null,
            redirectUri = null,
            refreshToken = "old-refresh",
            scope = null,
            clientId = "client",
            clientSecret = "secret",
            codeVerifier = null
        )

        assertEquals("new-access", result.accessToken)
        assertEquals("new-refresh", result.refreshToken)
    }

    @Test
    fun `getTokensUsingRefreshToken - Falls back to original refresh token when not refreshed`() = runTest {
        val request = mockRequestWithoutAuth()
        val client = mockClient()
        val accessToken = mockEncodedToken("new-access", type = ACCESS)

        coEvery { clientAuthenticationUtil.resolveClient(request, any(), any()) } returns client
        coEvery { tokenManager.refreshToken(client, "old-refresh") } returns listOf(accessToken)

        val result = controller.getTokens(
            request = request,
            grantType = "refresh_token",
            code = null,
            redirectUri = null,
            refreshToken = "old-refresh",
            scope = null,
            clientId = "client",
            clientSecret = "secret",
            codeVerifier = null
        )

        assertEquals("new-access", result.accessToken)
        assertEquals("old-refresh", result.refreshToken)
    }

    // --- getTokensUsingClientCredentials tests ---

    @Test
    fun `getTokensUsingClientCredentials - Returns access token without refresh or id token`() = runTest {
        val request = mockRequestWithoutAuth()
        val client = mockClient("my-client")
        val scope = mockk<Scope> { every { this@mockk.scope } returns "read" }
        val accessToken = mockEncodedToken("cc-access", listOf("read"))

        coEvery { clientAuthenticationUtil.resolveClient(request, any(), any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, "read") } returns listOf(scope)
        coEvery {
            accessTokenGenerator.generateAccessTokenForClient(clientId = "my-client", scopes = listOf("read"))
        } returns accessToken

        val result = controller.getTokens(
            request = request,
            grantType = "client_credentials",
            code = null,
            redirectUri = null,
            refreshToken = null,
            scope = "read",
            clientId = "my-client",
            clientSecret = "secret",
            codeVerifier = null
        )

        assertEquals("cc-access", result.accessToken)
        assertEquals("bearer", result.tokenType)
        assertNull(result.refreshToken)
        assertNull(result.idToken)
    }
}
