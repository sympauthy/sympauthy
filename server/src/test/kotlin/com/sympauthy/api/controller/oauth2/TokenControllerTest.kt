package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.OAUTH2_SURFACE
import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.mapper.TokenResourceMapper
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.token.TokenRequestManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_CLIENT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TARGET
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_GRANT_TYPE
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.IssuedTokens
import com.sympauthy.business.model.token.TokenIssuance
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.token.TokenRequest.TokenExchange.Companion.ACCESS_TOKEN_TYPE
import com.sympauthy.config.model.*
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class TokenControllerTest {

    @MockK
    lateinit var clientAuthenticationUtil: ClientAuthenticationUtil

    @MockK
    lateinit var tokenRequestManager: TokenRequestManager

    @SpyK
    var tokenResourceMapper = TokenResourceMapper()

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
        interactiveFlow = InteractiveFlowConfig(expiration = java.time.Duration.ofMinutes(30)),
        grant = GrantConfig(expiration = null),
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
            every { uri } returns URI.create("$OAUTH2_SURFACE/token")
        }.also {
            coEvery { dpopManager.validateDpopProof(any(), any()) } returns null
        }
    }

    /** A token the response is built around: it carries no scope, and it never expires. */
    private fun mockAccessToken(): EncodedAuthenticationToken = mockk {
        every { token } returns "access-jwt"
        every { scopes } returns emptyList()
        every { expirationDate } returns null
    }

    /**
     * Stubs [client] as the one [request] authenticates, through the resolution [grantType] admits, and the
     * token request manager as reading the request the controller hands it, then answering it with an access
     * token. Answers what was read, once the call has read it.
     */
    private fun stubIssuance(request: HttpRequest<*>, client: Client, grantType: GrantType): () -> TokenRequest? {
        if (grantType.publicClientAllowed) {
            coEvery { clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any()) } returns client
        } else {
            coEvery { clientAuthenticationUtil.resolveClient(request, any(), any()) } returns client
        }
        var read: TokenRequest? = null
        coEvery { tokenRequestManager.issueTokens(client, grantType, null, any()) } answers {
            val tokenRequest = arg<() -> TokenRequest>(3)().also { read = it }
            TokenIssuance(tokenRequest, IssuedTokens(mockAccessToken()))
        }
        return { read }
    }

    private suspend fun getTokens(
        request: HttpRequest<*>,
        grantType: String?,
        code: String? = null,
        refreshToken: String? = null,
        scope: String? = null,
        codeVerifier: String? = null,
        subjectToken: String? = null,
        subjectTokenType: String? = null,
        requestedSubject: String? = null,
        requestedTokenType: String? = null,
        resource: String? = null,
        audience: String? = null
    ) = controller.getTokens(
        request = request,
        grantType = grantType,
        code = code,
        refreshToken = refreshToken,
        scope = scope,
        clientId = "client",
        clientSecret = null,
        codeVerifier = codeVerifier,
        subjectToken = subjectToken,
        subjectTokenType = subjectTokenType,
        requestedSubject = requestedSubject,
        requestedTokenType = requestedTokenType,
        resource = resource,
        audience = audience
    )

    @Test
    fun `getTokens - client_credentials uses resolveClient, not resolveClientAllowingPublic`() = runTest {
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClient(request, any(), any())
        } throws oauth2ExceptionOf(INVALID_CLIENT, "authentication.wrong")

        assertThrows<OAuth2Exception> { getTokens(request, "client_credentials") }

        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClientAllowingPublic(any(), any(), any()) }
    }

    @Test
    fun `getTokens - token-exchange uses resolveClient, not resolveClientAllowingPublic`() = runTest {
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClient(request, any(), any())
        } throws oauth2ExceptionOf(INVALID_CLIENT, "authentication.wrong")

        assertThrows<OAuth2Exception> { getTokens(request, TOKEN_EXCHANGE) }

        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClientAllowingPublic(any(), any(), any()) }
    }

    @Test
    fun `getTokens - refresh_token uses resolveClientAllowingPublic, not resolveClient`() = runTest {
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
        } throws oauth2ExceptionOf(INVALID_CLIENT, "authentication.wrong")

        assertThrows<OAuth2Exception> { getTokens(request, "refresh_token", refreshToken = "some-token") }

        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClient(any(), any(), any()) }
    }

    @Test
    fun `getTokens - authorization_code uses resolveClientAllowingPublic, not resolveClient`() = runTest {
        val request = mockRequest()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, any(), any())
        } throws oauth2ExceptionOf(INVALID_CLIENT, "authentication.wrong")

        assertThrows<OAuth2Exception> { getTokens(request, "authorization_code", code = "some-code") }

        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClient(any(), any(), any()) }
    }

    @Test
    fun `getTokens - Binds the proof validation to the DPoP header, method and uri of the request`() = runTest {
        val request = mockRequest(dpopHeaders = listOf("a-proof"))

        assertThrows<OAuth2Exception> { getTokens(request, "unknown") }

        coVerify {
            dpopManager.validateDpopProof(
                listOf("a-proof"),
                DpopBoundRequest(method = "POST", uri = URI.create("$OAUTH2_SURFACE/token"), accessToken = null)
            )
        }
    }

    @Test
    fun `getTokens - Throws unsupported_grant_type for unknown grant`() = runTest {
        val exception = assertThrows<OAuth2Exception> { getTokens(mockRequest(), "unknown") }

        assertEquals(UNSUPPORTED_GRANT_TYPE, exception.errorCode)
        assertEquals("token.unsupported_grant_type", exception.detailsId)
    }

    @Test
    fun `getTokens - Throws invalid_request when grant_type is missing`() = runTest {
        val exception = assertThrows<OAuth2Exception> { getTokens(mockRequest(), null) }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        assertEquals(mapOf("param" to "grant_type"), exception.values)
    }

    @Test
    fun `getTokens - Reads the authorization_code grant into the code and its verifier`() = runTest {
        val request = mockRequest()
        val read = stubIssuance(request, mockk(), GrantType.AUTHORIZATION_CODE)

        val result = getTokens(request, "authorization_code", code = "the-code", codeVerifier = "the-verifier")

        assertEquals(TokenRequest.AuthorizationCode("the-code", "the-verifier"), read())
        assertEquals("access-jwt", result.accessToken)
    }

    @Test
    fun `getTokens - Refuses the authorization_code grant without code`() = runTest {
        val request = mockRequest()
        stubIssuance(request, mockk(), GrantType.AUTHORIZATION_CODE)

        val exception = assertThrows<OAuth2Exception> { getTokens(request, "authorization_code") }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        assertEquals(mapOf("param" to "code"), exception.values)
    }

    @Test
    fun `getTokens - Refuses the refresh_token grant without refresh_token`() = runTest {
        val request = mockRequest()
        stubIssuance(request, mockk(), GrantType.REFRESH_TOKEN)

        val exception = assertThrows<OAuth2Exception> { getTokens(request, "refresh_token") }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        assertEquals(mapOf("param" to "refresh_token"), exception.values)
    }

    @Test
    fun `getTokens - Reads the client_credentials grant into the scope it asks for`() = runTest {
        val request = mockRequest()
        val read = stubIssuance(request, mockk(), GrantType.CLIENT_CREDENTIALS)

        getTokens(request, "client_credentials", scope = "read")

        assertEquals(TokenRequest.ClientCredentials("read"), read())
    }

    @Test
    fun `getTokens - Reads the token exchange grant into its subject and audience`() = runTest {
        val request = mockRequest()
        val read = stubIssuance(request, mockk(), GrantType.TOKEN_EXCHANGE)

        val result = getTokens(
            request, TOKEN_EXCHANGE,
            subjectToken = "a-token", subjectTokenType = ACCESS_TOKEN_TYPE, requestedSubject = "a-subject",
            requestedTokenType = ACCESS_TOKEN_TYPE, audience = "backend"
        )

        assertEquals(TokenRequest.TokenExchange("a-token", "a-subject", "backend"), read())
        assertEquals(ACCESS_TOKEN_TYPE, result.issuedTokenType)
    }

    @Test
    fun `getTokens - Refuses the token exchange grant without subject_token`() = runTest {
        val request = mockRequest()
        stubIssuance(request, mockk(), GrantType.TOKEN_EXCHANGE)

        val exception = assertThrows<OAuth2Exception> {
            getTokens(request, TOKEN_EXCHANGE, subjectTokenType = ACCESS_TOKEN_TYPE, requestedSubject = "a-subject")
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        assertEquals(mapOf("param" to "subject_token"), exception.values)
    }

    @Test
    fun `getTokens - Refuses a requested token type other than an access token before the subject token type`() =
        runTest {
            val request = mockRequest()
            stubIssuance(request, mockk(), GrantType.TOKEN_EXCHANGE)

            val exception = assertThrows<OAuth2Exception> {
                getTokens(
                    request, TOKEN_EXCHANGE,
                    subjectToken = "a-token", subjectTokenType = "unexpected", requestedSubject = "a-subject",
                    requestedTokenType = "urn:ietf:params:oauth:token-type:id_token"
                )
            }

            assertEquals(INVALID_REQUEST, exception.errorCode)
            assertEquals("token_exchange.unsupported_requested_token_type", exception.detailsId)
        }

    @Test
    fun `getTokens - Refuses a subject token type other than an access token before a resource`() = runTest {
        val request = mockRequest()
        stubIssuance(request, mockk(), GrantType.TOKEN_EXCHANGE)

        val exception = assertThrows<OAuth2Exception> {
            getTokens(
                request, TOKEN_EXCHANGE,
                subjectToken = "a-token", subjectTokenType = "unexpected", requestedSubject = "a-subject",
                resource = "https://backend.example.com"
            )
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token_exchange.unsupported_subject_token_type", exception.detailsId)
        assertEquals(mapOf("type" to "unexpected"), exception.values)
    }

    @Test
    fun `getTokens - Refuses a token exchange carrying resource`() = runTest {
        val request = mockRequest()
        stubIssuance(request, mockk(), GrantType.TOKEN_EXCHANGE)

        val exception = assertThrows<OAuth2Exception> {
            getTokens(
                request, TOKEN_EXCHANGE,
                subjectToken = "a-token", subjectTokenType = ACCESS_TOKEN_TYPE, requestedSubject = "a-subject",
                resource = "https://backend.example.com", audience = "backend"
            )
        }

        assertEquals(INVALID_TARGET, exception.errorCode)
        assertEquals("token_exchange.unsupported_resource", exception.detailsId)
    }

    private companion object {
        const val TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange"
    }
}
