package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.config.model.*
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class IntrospectionControllerTest {

    @MockK
    lateinit var tokenManager: TokenManager

    @MockK
    lateinit var clientAuthenticationUtil: ClientAuthenticationUtil

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
    lateinit var controller: IntrospectionController

    private fun mockRequest(): HttpRequest<*> {
        val headers = mockk<HttpHeaders> {
            every { authorization } returns Optional.empty()
        }
        return mockk<HttpRequest<*>> {
            every { this@mockk.headers } returns headers
        }
    }

    private fun mockClient(id: String = "test-client"): Client {
        return mockk {
            every { this@mockk.id } returns id
            every { audience } returns mockk {
                every { tokenAudience } returns "https://test-audience"
            }
        }
    }

    @Test
    fun `introspectToken - Returns active response for valid token`() = runTest {
        val request = mockRequest()
        val client = mockClient()
        val userId = UUID.randomUUID()
        val tokenId = UUID.randomUUID()
        val issueDate = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        val expirationDate = LocalDateTime.of(2025, 1, 1, 1, 0, 0)

        val token = mockk<AuthenticationToken> {
            every { id } returns tokenId
            every { this@mockk.clientId } returns "test-client"
            every { this@mockk.userId } returns userId
            every { allScopes } returns listOf("openid", "profile")
            every { dpopJkt } returns null
            every { this@mockk.issueDate } returns issueDate
            every { this@mockk.expirationDate } returns expirationDate
        }

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", null) } returns token

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertTrue(result.active)
        assertEquals("openid profile", result.scope)
        assertEquals("test-client", result.clientId)
        assertEquals("Bearer", result.tokenType)
        assertEquals(userId.toString(), result.sub)
        assertEquals("https://test-audience", result.aud)
        assertEquals("https://issuer.example.com", result.iss)
        assertEquals(tokenId.toString(), result.jti)
        assertNotNull(result.exp)
        assertNotNull(result.iat)
    }

    @Test
    fun `introspectToken - Returns inactive response for invalid token`() = runTest {
        val request = mockRequest()
        val client = mockClient()

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "bad-token", null) } returns null

        val result = controller.introspectToken(
            request = request,
            token = "bad-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertFalse(result.active)
        assertNull(result.scope)
        assertNull(result.clientId)
        assertNull(result.sub)
        assertNull(result.jti)
    }

    @Test
    fun `introspectToken - Throws when token parameter is missing`() = runTest {
        val request = mockRequest()
        val client = mockClient()

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client

        val exception = assertThrows<OAuth2Exception> {
            controller.introspectToken(
                request = request,
                token = null,
                tokenTypeHint = null,
                clientId = "test-client",
                clientSecret = "secret"
            )
        }
        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
    }

    @Test
    fun `introspectToken - Throws when client authentication fails`() = runTest {
        val request = mockRequest()

        coEvery {
            clientAuthenticationUtil.resolveClient(request, "bad-client", "wrong")
        } throws oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")

        assertThrows<OAuth2Exception> {
            controller.introspectToken(
                request = request,
                token = "some-token",
                tokenTypeHint = null,
                clientId = "bad-client",
                clientSecret = "wrong"
            )
        }
    }

    @Test
    fun `introspectToken - Returns DPoP token type when dpopJkt is present`() = runTest {
        val request = mockRequest()
        val client = mockClient()
        val token = mockk<AuthenticationToken> {
            every { id } returns UUID.randomUUID()
            every { clientId } returns "test-client"
            every { userId } returns null
            every { allScopes } returns emptyList()
            every { dpopJkt } returns "some-thumbprint"
            every { issueDate } returns LocalDateTime.of(2025, 1, 1, 0, 0, 0)
            every { expirationDate } returns null
        }

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", null) } returns token

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertTrue(result.active)
        assertEquals("DPoP", result.tokenType)
    }

    @Test
    fun `introspectToken - Uses client ID as subject for client_credentials tokens`() = runTest {
        val request = mockRequest()
        val client = mockClient()
        val token = mockk<AuthenticationToken> {
            every { id } returns UUID.randomUUID()
            every { clientId } returns "test-client"
            every { userId } returns null
            every { allScopes } returns listOf("read")
            every { dpopJkt } returns null
            every { issueDate } returns LocalDateTime.of(2025, 1, 1, 0, 0, 0)
            every { expirationDate } returns null
        }

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", null) } returns token

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = null,
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertTrue(result.active)
        assertEquals("test-client", result.sub)
    }

    @Test
    fun `introspectToken - Passes token_type_hint to manager`() = runTest {
        val request = mockRequest()
        val client = mockClient()

        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns client
        coEvery { tokenManager.introspectToken(client, "the-token", "refresh_token") } returns null

        val result = controller.introspectToken(
            request = request,
            token = "the-token",
            tokenTypeHint = "refresh_token",
            clientId = "test-client",
            clientSecret = "secret"
        )

        assertFalse(result.active)
        coVerify(exactly = 1) { tokenManager.introspectToken(client, "the-token", "refresh_token") }
    }
}
