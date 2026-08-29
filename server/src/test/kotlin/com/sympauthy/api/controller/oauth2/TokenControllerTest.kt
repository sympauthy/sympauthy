package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.ClientGrantScopesResult
import com.sympauthy.business.manager.auth.ClientScopeGrantingManager
import com.sympauthy.business.manager.auth.oauth2.*
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_GRANT_TYPE
import com.sympauthy.config.model.*
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class TokenControllerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

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

    @MockK
    lateinit var clientScopeGrantingManager: ClientScopeGrantingManager

    @MockK
    lateinit var tokenExchangeManager: TokenExchangeManager

    @MockK
    lateinit var dpopManager: DpopManager

    private val uncheckedAuthConfig: AuthConfig = EnabledAuthConfig(
        issuer = "https://issuer.example.com",
        token = TokenConfig(
            accessExpiration = java.time.Duration.ofHours(1),
            idExpiration = java.time.Duration.ofHours(1),
            refreshEnabled = true,
            refreshExpiration = java.time.Duration.ofDays(30),
            dpopRequired = false
        ),
        authorizationCode = AuthorizationCodeConfig(
            expiration = java.time.Duration.ofMinutes(30)
        ),
        identifierClaims = emptyList(),
        userMergingEnabled = false,
        byPassword = ByPasswordConfig(enabled = false)
    )

    @InjectMockKs
    lateinit var controller: TokenController

    /** A request the DPoP manager is stubbed to find no proof on, whatever [dpopHeaders] it carries. */
    private fun mockRequest(dpopHeaders: List<String> = emptyList()): HttpRequest<*> {
        val headers = mockk<HttpHeaders> {
            every { getAll(DpopManager.DPOP_HEADER) } returns dpopHeaders
        }
        return mockk<HttpRequest<*>> {
            every { this@mockk.headers } returns headers
            every { method } returns HttpMethod.POST
            every { uri } returns URI.create("/api/oauth2/token")
        }.also {
            every { dpopManager.validateDpopProof(any(), any()) } returns null
        }
    }

    private fun mockClient(): Client = mockk {
        every { supportsGrantType(any()) } returns true
    }

    /** A client the issued token is named after, which only the client credentials grant does. */
    private fun mockClientIdentifiedAs(id: String): Client = mockClient().also {
        every { it.id } returns id
        every { it.audience } returns mockk { every { tokenAudience } returns "https://test-audience" }
    }

    private fun mockOAuth2(redirectUri: String): InteractiveFlowSessionOAuth2 = mockk {
        every { this@mockk.redirectUri } returns redirectUri
    }

    /** A token the response only carries the encoded form of. */
    private fun mockEncodedToken(token: String): EncodedAuthenticationToken = mockk {
        every { this@mockk.token } returns token
    }

    /** A token the response is built around: its scopes are reported, and its lifetime with them. */
    private fun mockAccessToken(
        token: String,
        scopes: List<String> = emptyList(),
        issueDate: LocalDateTime = LocalDateTime.of(2024, 1, 1, 0, 0),
        expirationDate: LocalDateTime? = null
    ): EncodedAuthenticationToken = mockk {
        every { this@mockk.token } returns token
        every { this@mockk.scopes } returns scopes
        every { this@mockk.expirationDate } returns expirationDate
        if (expirationDate != null) {
            every { this@mockk.issueDate } returns issueDate
        }
    }

    // --- getTokens routing tests ---

    @Test
    fun `getTokens - client_credentials uses resolveClient, not resolveClientAllowingPublic`() = runTest {
        val request = mockRequest()
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

        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClientAllowingPublic(any(), any(), any()) }
    }

    @Test
    fun `getTokens - token-exchange uses resolveClient, not resolveClientAllowingPublic`() = runTest {
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClient(request, any(), any())
        } throws oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")

        assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "urn:ietf:params:oauth:grant-type:token-exchange",
                code = null,
                redirectUri = null,
                refreshToken = null,
                scope = null,
                clientId = "any-client",
                clientSecret = null,
                codeVerifier = null
            )
        }

        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClientAllowingPublic(any(), any(), any()) }
    }

    @Test
    fun `getTokens - token-exchange without subject_token throws invalid_request`() = runTest {
        val request = mockRequest()
        val client = mockClient()
        coEvery { clientAuthenticationUtil.resolveClient(request, any(), any()) } returns client

        val exception = assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
                grantType = "urn:ietf:params:oauth:grant-type:token-exchange",
                code = null,
                redirectUri = null,
                refreshToken = null,
                scope = null,
                clientId = "any-client",
                clientSecret = null,
                codeVerifier = null
            )
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        coVerify(exactly = 0) {
            tokenExchangeManager.exchangeForActAsToken(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `getTokens - refresh_token uses resolveClientAllowingPublic, not resolveClient`() = runTest {
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
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

        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClient(any(), any(), any()) }
    }

    @Test
    fun `getTokens - authorization_code uses resolveClientAllowingPublic, not resolveClient`() = runTest {
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
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

        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClient(any(), any(), any()) }
    }

    @Test
    fun `getTokens - Binds the proof validation to the DPoP header, method and uri of the request`() = runTest {
        val request = mockRequest(dpopHeaders = listOf("a-proof"))

        assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = request,
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

        verify {
            dpopManager.validateDpopProof(
                listOf("a-proof"),
                DpopBoundRequest(method = "POST", uri = URI.create("/api/oauth2/token"))
            )
        }
    }

    @Test
    fun `getTokens - Throws unsupported_grant_type for unknown grant`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            controller.getTokens(
                request = mockRequest(),
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
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
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
        val request = mockRequest()
        val session = mockk<CompletedInteractiveFlowSession>()
        val oauth2 = mockOAuth2("https://example.com/callback")
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
        } returns mockClient()
        coEvery { sessionManager.findByCodeOrNull("the-code") } returns session
        coEvery { authorizeFlowManager.checkCanIssueToken(session, oauth2, any()) } returns (session to oauth2)
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns oauth2

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
    fun `getTokensUsingAuthorizationCode - Throws invalid_grant when session is not found`() = runTest {
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
        } returns mockClient()
        coEvery { sessionManager.findByCodeOrNull("the-code") } returns null
        // No OAuth2 record is fetched when there is no session; checkCanIssueToken rejects the null session.
        coEvery {
            authorizeFlowManager.checkCanIssueToken(null, null, any())
        } throws businessExceptionOf("token.expired")

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
                codeVerifier = null
            )
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `getTokensUsingAuthorizationCode - Throws invalid_grant when oauth2 record is missing`() = runTest {
        val request = mockRequest()
        val session = mockk<CompletedInteractiveFlowSession>()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
        } returns mockClient()
        coEvery { sessionManager.findByCodeOrNull("the-code") } returns session
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns null
        coEvery {
            authorizeFlowManager.checkCanIssueToken(session, null, any())
        } throws businessExceptionOf("token.expired")

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
                codeVerifier = null
            )
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `getTokensUsingAuthorizationCode - Throws when PKCE verification fails`() = runTest {
        val request = mockRequest()
        val session = mockk<CompletedInteractiveFlowSession>()
        val oauth2 = mockOAuth2("https://example.com/callback")
        every { oauth2.codeChallenge } returns "stored-challenge"
        every { oauth2.codeChallengeMethod } returns CodeChallengeMethod.S256
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
        } returns mockClient()
        coEvery { sessionManager.findByCodeOrNull("the-code") } returns session
        coEvery { authorizeFlowManager.checkCanIssueToken(session, oauth2, any()) } returns (session to oauth2)
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns oauth2
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
        val request = mockRequest()
        val accessToken = mockAccessToken("access-jwt", listOf("openid"))
        val refreshToken = mockEncodedToken("refresh-jwt")
        val idToken = mockEncodedToken("id-jwt")
        val session = mockk<CompletedInteractiveFlowSession>()
        val oauth2 = mockOAuth2("https://example.com/callback")
        every { oauth2.codeChallenge } returns null
        every { oauth2.codeChallengeMethod } returns null
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
        } returns mockClient()
        coEvery { sessionManager.findByCodeOrNull("the-code") } returns session
        coEvery { authorizeFlowManager.checkCanIssueToken(session, oauth2, any()) } returns (session to oauth2)
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns oauth2
        every { pkceManager.verifyCodeVerifier(null, null, null) } just runs
        coEvery { tokenManager.generateTokens(session, oauth2, any(), dpopJkt = null) } returns GenerateTokenResult(
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
        val request = mockRequest()
        val client = mockClient()
        coEvery { clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any()) } returns client

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
        val request = mockRequest()
        val client = mockClient()
        val accessToken = mockAccessToken("new-access", listOf("openid"))
        val newRefreshToken = mockEncodedToken("new-refresh")
        every { accessToken.type } returns ACCESS
        every { newRefreshToken.type } returns REFRESH

        coEvery { clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any()) } returns client
        coEvery { tokenManager.refreshToken(client, "old-refresh", dpopJkt = null) } returns listOf(
            accessToken,
            newRefreshToken
        )

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
        val request = mockRequest()
        val client = mockClient()
        val accessToken = mockAccessToken("new-access")
        every { accessToken.type } returns ACCESS

        coEvery { clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any()) } returns client
        coEvery { tokenManager.refreshToken(client, "old-refresh", dpopJkt = null) } returns listOf(accessToken)

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
        val request = mockRequest()
        val client = mockClientIdentifiedAs("my-client")
        val scope = mockk<com.sympauthy.business.model.oauth2.ClientScope> { every { this@mockk.scope } returns "read" }
        val accessToken = mockAccessToken("cc-access", listOf("read"))

        coEvery { clientAuthenticationUtil.resolveClient(request, any(), any()) } returns client
        coEvery { scopeManager.parseRequestedClientScopes(client, "read") } returns listOf(scope)
        coEvery { clientScopeGrantingManager.grantClientScopes(client, listOf(scope)) } returns ClientGrantScopesResult(
            requestedScopes = listOf(scope),
            results = listOf(ScopeGrantingMethodResult(grantedScopes = listOf(scope), declinedScopes = emptyList()))
        )
        coEvery {
            accessTokenGenerator.generateAccessTokenForClient(
                clientId = "my-client",
                tokenAudience = any(),
                clientScopes = listOf("read"),
                dpopJkt = null
            )
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
        assertNull(result.expiresIn, "a token without an expiration date has no lifetime to report")
    }

    // --- expires_in tests ---

    @Test
    fun `getTokens - Returns the lifetime of the access token in seconds`() = runTest {
        val request = mockRequest()
        val client = mockClientIdentifiedAs("my-client")
        val accessToken = mockAccessToken(
            token = "cc-access",
            issueDate = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            expirationDate = LocalDateTime.of(2024, 1, 1, 13, 0, 0)
        )

        coEvery { clientAuthenticationUtil.resolveClient(request, any(), any()) } returns client
        coEvery { scopeManager.parseRequestedClientScopes(client, null) } returns emptyList()
        coEvery { clientScopeGrantingManager.grantClientScopes(client, emptyList()) } returns ClientGrantScopesResult(
            requestedScopes = emptyList(),
            results = emptyList()
        )
        coEvery {
            accessTokenGenerator.generateAccessTokenForClient(
                clientId = "my-client",
                tokenAudience = any(),
                clientScopes = emptyList(),
                dpopJkt = null
            )
        } returns accessToken

        val result = controller.getTokens(
            request = request,
            grantType = "client_credentials",
            code = null,
            redirectUri = null,
            refreshToken = null,
            scope = null,
            clientId = "my-client",
            clientSecret = "secret",
            codeVerifier = null
        )

        assertEquals(3600, result.expiresIn)
    }
}
