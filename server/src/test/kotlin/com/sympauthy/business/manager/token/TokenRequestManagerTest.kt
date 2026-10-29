package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNAUTHORIZED_CLIENT
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.IssuedTokens
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.TokenMetrics
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class TokenRequestManagerTest {

    @MockK
    lateinit var codeExchangeManager: CodeExchangeManager

    @MockK
    lateinit var tokenRefreshManager: TokenRefreshManager

    @MockK
    lateinit var clientCredentialsManager: ClientCredentialsManager

    @MockK
    lateinit var tokenExchangeManager: TokenExchangeManager

    @SpyK
    var tokenMetrics = TokenMetrics(OpenTelemetry.noop())

    @InjectMockKs
    lateinit var manager: TokenRequestManager

    private fun mockClient(supportsGrantType: Boolean = true): Client = mockk {
        every { id } returns CLIENT_ID
        every { supportsGrantType(any()) } returns supportsGrantType
    }

    @Test
    fun `issueTokens - Refuses a client not supporting the grant type before reading the request`() = runTest {
        val client = mockClient(supportsGrantType = false)
        var read = false

        val exception = assertThrows<OAuth2Exception> {
            manager.issueTokens(client, GrantType.REFRESH_TOKEN, null) {
                read = true
                TokenRequest.RefreshToken("a-refresh-token")
            }
        }

        assertEquals(UNAUTHORIZED_CLIENT, exception.errorCode)
        assertEquals("token.unauthorized_grant_type", exception.detailsId)
        assertEquals(false, read)
        verify { client.supportsGrantType(GrantType.REFRESH_TOKEN) }
        coVerify(exactly = 0) { tokenRefreshManager.refresh(any(), any(), any()) }
        verify {
            tokenMetrics.recordRequest(
                "refresh_token", "bearer", CLIENT_ID, any(), Outcome.Refused("token.unauthorized_grant_type")
            )
        }
    }

    @Test
    fun `issueTokens - Records a request its reading refuses under its grant type and client`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            manager.issueTokens(mockClient(), GrantType.AUTHORIZATION_CODE, null) {
                throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to "code")
            }
        }

        assertEquals("token.missing_param", exception.detailsId)
        coVerify(exactly = 0) { codeExchangeManager.exchange(any(), any(), any()) }
        verify {
            tokenMetrics.recordRequest(
                "authorization_code", "bearer", CLIENT_ID, any(), Outcome.Refused("token.missing_param")
            )
        }
    }

    @Test
    fun `issueTokens - Records a refusal of the use case under its grant type and client`() = runTest {
        val client = mockClient()
        val request = TokenRequest.AuthorizationCode("the-code", "wrong-verifier")
        coEvery {
            codeExchangeManager.exchange(client, request, null)
        } throws oauth2ExceptionOf(INVALID_GRANT, "token.pkce.invalid_code_verifier")

        assertThrows<OAuth2Exception> {
            manager.issueTokens(client, GrantType.AUTHORIZATION_CODE, null) { request }
        }

        verify {
            tokenMetrics.recordRequest(
                "authorization_code", "bearer", CLIENT_ID, any(), Outcome.Refused("token.pkce.invalid_code_verifier")
            )
        }
    }

    @Test
    fun `issueTokens - Issues the tokens of the code exchange, bound to the key`() = runTest {
        val client = mockClient()
        val request = TokenRequest.AuthorizationCode("the-code", "the-verifier")
        val tokens = IssuedTokens(mockk())
        coEvery { codeExchangeManager.exchange(client, request, "a-jkt") } returns tokens

        val issuance = manager.issueTokens(client, GrantType.AUTHORIZATION_CODE, "a-jkt") { request }

        assertSame(request, issuance.request)
        assertSame(tokens, issuance.tokens)
        verify { tokenMetrics.recordRequest("authorization_code", "dpop", CLIENT_ID, any(), Outcome.Success) }
    }

    @Test
    fun `issueTokens - Issues the tokens of the refresh`() = runTest {
        val client = mockClient()
        val request = TokenRequest.RefreshToken("a-refresh-token")
        val tokens = IssuedTokens(mockk())
        coEvery { tokenRefreshManager.refresh(client, request, null) } returns tokens

        val issuance = manager.issueTokens(client, GrantType.REFRESH_TOKEN, null) { request }

        assertSame(tokens, issuance.tokens)
    }

    @Test
    fun `issueTokens - Issues the token of the client_credentials grant`() = runTest {
        val client = mockClient()
        val request = TokenRequest.ClientCredentials("read")
        val tokens = IssuedTokens(mockk())
        coEvery { clientCredentialsManager.issue(client, request, null) } returns tokens

        val issuance = manager.issueTokens(client, GrantType.CLIENT_CREDENTIALS, null) { request }

        assertSame(tokens, issuance.tokens)
        verify { tokenMetrics.recordRequest("client_credentials", "bearer", CLIENT_ID, any(), Outcome.Success) }
    }

    @Test
    fun `issueTokens - Issues the access token of the token exchange alone`() = runTest {
        val client = mockClient()
        val request = TokenRequest.TokenExchange("a-token", "a-subject", audience = null)
        val accessToken = mockk<EncodedAuthenticationToken>()
        coEvery { tokenExchangeManager.exchangeForActAsToken(client, request, null) } returns accessToken

        val issuance = manager.issueTokens(client, GrantType.TOKEN_EXCHANGE, null) { request }

        assertEquals(IssuedTokens(accessToken = accessToken), issuance.tokens)
    }


    @Test
    fun `issueTokens - Refuses a request read for another grant than the one checked, and serves none`() =
        runTest {
            val client = mockClient()

            val exception = assertThrows<BusinessException> {
                manager.issueTokens(client, GrantType.CLIENT_CREDENTIALS, null) {
                    TokenRequest.AuthorizationCode("the-code", "the-verifier")
                }
            }

            assertEquals("token.mismatching_grant_type", exception.detailsId)
            assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
            assertEquals(
                mapOf("grantType" to "client_credentials", "requestGrantType" to "authorization_code"),
                exception.values
            )
            coVerify(exactly = 0) { codeExchangeManager.exchange(any(), any(), any()) }
            coVerify(exactly = 0) { clientCredentialsManager.issue(any(), any(), any()) }
        }

    private companion object {
        const val CLIENT_ID = "client"
    }
}
