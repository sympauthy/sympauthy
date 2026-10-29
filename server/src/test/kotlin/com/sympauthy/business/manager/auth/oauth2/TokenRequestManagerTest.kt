package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.ClientGrantScopesResult
import com.sympauthy.business.manager.auth.ClientScopeGrantingManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_SCOPE
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNAUTHORIZED_CLIENT
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.TokenMetrics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
    lateinit var authorizationCodeManager: AuthorizationCodeManager

    @MockK
    lateinit var pkceManager: PkceManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var clientScopeGrantingManager: ClientScopeGrantingManager

    @MockK
    lateinit var tokenManager: TokenManager

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

    private fun mockCode(): AuthorizationCode = mockk {
        every { codeChallenge } returns "stored-challenge"
        every { codeChallengeMethod } returns CodeChallengeMethod.S256
    }

    @Test
    fun `exchangeAuthorizationCode - Refuses the code as the check does, before reading the verifier`() = runTest {
        val client = mockClient()
        coEvery { authorizationCodeManager.findByCodeOrNull("the-code") } returns null
        coEvery {
            authorizationCodeManager.checkCanExchange(null, client)
        } throws oauth2ExceptionOf(INVALID_GRANT, "token.expired")

        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeAuthorizationCode(client, "the-code", "the-verifier", null)
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.expired", exception.detailsId)
        verify(exactly = 0) { pkceManager.verifyCodeVerifier(any(), any(), any()) }
        coVerify(exactly = 0) { tokenManager.generateTokensForUser(any(), any(), any(), any()) }
    }

    @Test
    fun `exchangeAuthorizationCode - Throws invalid_grant when PKCE verification fails`() = runTest {
        val client = mockClient()
        val code = mockCode()
        coEvery { authorizationCodeManager.findByCodeOrNull("the-code") } returns code
        coEvery { authorizationCodeManager.checkCanExchange(code, client) } returns (code to mockk<Grant>())
        every {
            pkceManager.verifyCodeVerifier("wrong-verifier", "stored-challenge", CodeChallengeMethod.S256)
        } throws businessExceptionOf(detailsId = "token.pkce.invalid_code_verifier")

        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeAuthorizationCode(client, "the-code", "wrong-verifier", null)
        }

        assertEquals(INVALID_GRANT, exception.errorCode)
        assertEquals("token.pkce.invalid_code_verifier", exception.detailsId)
        coVerify(exactly = 0) { tokenManager.generateTokensForUser(any(), any(), any(), any()) }
        verify {
            tokenMetrics.recordRequest(
                "authorization_code", "bearer", CLIENT_ID, any(),
                Outcome.Refused("token.pkce.invalid_code_verifier")
            )
        }
    }

    @Test
    fun `exchangeAuthorizationCode - Issues the tokens of the grant the code names, bound to the key`() = runTest {
        val client = mockClient()
        val code = mockCode()
        val grant = mockk<Grant>()
        val tokens = GenerateTokenResult(mockk(), null, null)
        coEvery { authorizationCodeManager.findByCodeOrNull("the-code") } returns code
        coEvery { authorizationCodeManager.checkCanExchange(code, client) } returns (code to grant)
        every {
            pkceManager.verifyCodeVerifier("the-verifier", "stored-challenge", CodeChallengeMethod.S256)
        } just runs
        coEvery { tokenManager.generateTokensForUser(code, grant, client, dpopJkt = "a-jkt") } returns tokens

        val result = manager.exchangeAuthorizationCode(client, "the-code", "the-verifier", "a-jkt")

        assertSame(tokens, result)
        verify { tokenMetrics.recordRequest("authorization_code", "dpop", CLIENT_ID, any(), Outcome.Success) }
    }

    @Test
    fun `refreshTokens - Refuses a client that does not support the grant type before reading the token`() =
        runTest {
            val client = mockClient(supportsGrantType = false)

            val exception = assertThrows<OAuth2Exception> {
                manager.refreshTokens(client, "a-refresh-token", null)
            }

            assertEquals(UNAUTHORIZED_CLIENT, exception.errorCode)
            assertEquals("token.unauthorized_grant_type", exception.detailsId)
            verify { client.supportsGrantType(GrantType.REFRESH_TOKEN) }
            coVerify(exactly = 0) { tokenManager.refreshToken(any(), any(), any()) }
            verify {
                tokenMetrics.recordRequest(
                    "refresh_token", "bearer", CLIENT_ID, any(), Outcome.Refused("token.unauthorized_grant_type")
                )
            }
        }

    @Test
    fun `generateClientToken - Issues an access token for the client scopes granted`() = runTest {
        val client = mockClient()
        val scope = mockk<ClientScope> { every { this@mockk.scope } returns "read" }
        val accessToken = mockk<EncodedAuthenticationToken>()
        coEvery { scopeManager.parseRequestedClientScopes(client, "read") } returns listOf(scope)
        coEvery { clientScopeGrantingManager.grantClientScopes(client, listOf(scope)) } returns ClientGrantScopesResult(
            requestedScopes = listOf(scope),
            results = listOf(ScopeGrantingMethodResult(grantedScopes = listOf(scope), declinedScopes = emptyList()))
        )
        coEvery {
            tokenManager.generateAccessTokenForClient(client = client, clientScopes = listOf("read"), dpopJkt = null)
        } returns accessToken

        val result = manager.generateClientToken(client, "read", null)

        assertSame(accessToken, result)
        verify { tokenMetrics.recordRequest("client_credentials", "bearer", CLIENT_ID, any(), Outcome.Success) }
    }

    @Test
    fun `generateClientToken - Throws invalid_scope when a requested scope is refused`() = runTest {
        val client = mockClient()
        coEvery {
            scopeManager.parseRequestedClientScopes(client, "doesnotexist")
        } throws businessExceptionOf("scope.unsupported", "scope" to "doesnotexist")

        val exception = assertThrows<OAuth2Exception> {
            manager.generateClientToken(client, "doesnotexist", null)
        }

        assertEquals(INVALID_SCOPE, exception.errorCode)
        assertEquals("scope.unsupported", exception.detailsId)
        assertEquals(mapOf("scope" to "doesnotexist"), exception.values)
        coVerify(exactly = 0) { tokenManager.generateAccessTokenForClient(any(), any(), any()) }
    }

    @Test
    fun `exchangeAuthorizationCode - Throws invalid_request when code is missing`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeAuthorizationCode(mockClient(), null, "the-verifier", null)
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        assertEquals(mapOf("param" to "code"), exception.values)
        verify {
            tokenMetrics.recordRequest(
                "authorization_code", "bearer", CLIENT_ID, any(), Outcome.Refused("token.missing_param")
            )
        }
    }

    @Test
    fun `exchangeAuthorizationCode - Refuses a client not supporting the grant type before a missing code`() =
        runTest {
            val exception = assertThrows<OAuth2Exception> {
                manager.exchangeAuthorizationCode(mockClient(supportsGrantType = false), null, null, null)
            }

            assertEquals(UNAUTHORIZED_CLIENT, exception.errorCode)
            assertEquals("token.unauthorized_grant_type", exception.detailsId)
        }

    @Test
    fun `refreshTokens - Throws invalid_request when refresh_token is missing`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            manager.refreshTokens(mockClient(), null, null)
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        assertEquals(mapOf("param" to "refresh_token"), exception.values)
        coVerify(exactly = 0) { tokenManager.refreshToken(any(), any(), any()) }
    }

    @Test
    fun `exchangeForActAsToken - Throws invalid_request when subject_token is missing`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeForActAsToken(
                client = mockClient(),
                subjectToken = null,
                subjectTokenType = TokenExchangeManager.ACCESS_TOKEN_TYPE,
                requestedSubject = "a-subject",
                requestedTokenType = null,
                resource = null,
                audience = null,
                dpopJkt = null
            )
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        assertEquals(mapOf("param" to "subject_token"), exception.values)
        coVerify(exactly = 0) {
            tokenExchangeManager.exchangeForActAsToken(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `exchangeForActAsToken - Refuses a requested token type other than an access token`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            manager.exchangeForActAsToken(
                client = mockClient(),
                subjectToken = "a-token",
                subjectTokenType = TokenExchangeManager.ACCESS_TOKEN_TYPE,
                requestedSubject = "a-subject",
                requestedTokenType = "urn:ietf:params:oauth:token-type:id_token",
                resource = null,
                audience = null,
                dpopJkt = null
            )
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token_exchange.unsupported_requested_token_type", exception.detailsId)
        coVerify(exactly = 0) {
            tokenExchangeManager.exchangeForActAsToken(any(), any(), any(), any(), any(), any(), any())
        }
    }

    private companion object {
        const val CLIENT_ID = "client"
    }
}
