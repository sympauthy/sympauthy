package com.sympauthy.security

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class AccessTokenValidatorTest {

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var tokenManager: TokenManager

    @MockK
    lateinit var dpopManager: DpopManager

    @MockK
    lateinit var jwtManager: JwtManager

    private fun validator() = AccessTokenValidator(
        clientManager, scopeManager, tokenManager, dpopManager, jwtManager, DpopTokenReader()
    )

    private fun stubToken(token: AuthenticationToken) {
        val decodedToken = mockk<DecodedJwt>()
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, ENCODED_TOKEN) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns token
    }

    private fun stubBindingAccepted() {
        coJustRun { dpopManager.validateAccessTokenBinding(any(), any(), any(), any()) }
    }

    private fun bearerRequest() = HttpRequest.GET<Any>(USERINFO_PATH)
        .header("Authorization", "Bearer $ENCODED_TOKEN")

    @Test
    fun `validateToken - Refuse with 401 a token issued to a client the configuration no longer declares`() =
        runTest {
            stubToken(mockk { every { grant } returns mockk { every { clientId } returns CLIENT_ID } })
            stubBindingAccepted()
            coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns null

            val exception = assertThrows<LocalizedHttpException> {
                validator().validateToken(ENCODED_TOKEN, bearerRequest()).asFlow().single()
            }

            assertEquals(UNAUTHORIZED, exception.status)
            assertEquals("grant.client_disabled", exception.detailsId)
            assertEquals(CLIENT_ID, exception.values["clientId"])
        }

    @Test
    fun `validateToken - Carry the client a client_credentials token was issued to`() = runTest {
        val client = mockk<Client>()
        stubToken(mockk {
            every { grant } returns mockk {
                every { clientId } returns CLIENT_ID
                every { userId } returns null
                every { clientScopes } returns emptyList()
            }
        })
        stubBindingAccepted()
        coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns client

        val authentication = validator().validateToken(ENCODED_TOKEN, bearerRequest()).asFlow().single()

        assertSame(client, (authentication as ClientAuthentication).client)
    }

    @Test
    fun `validateToken - Carry the client a token issued for an end-user was issued to`() = runTest {
        val client = mockk<Client>()
        stubToken(mockk {
            every { grant } returns mockk {
                every { clientId } returns CLIENT_ID
                every { userId } returns UUID.randomUUID()
                every { consentedScopes } returns emptyList()
                every { grantedScopes } returns emptyList()
            }
        })
        stubBindingAccepted()
        coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns client

        val authentication = validator().validateToken(ENCODED_TOKEN, bearerRequest()).asFlow().single()

        assertSame(client, (authentication as UserAuthentication).client)
    }

    @Test
    fun `validateToken - Check the binding of a token presented under the DPoP scheme against its proof`() = runTest {
        val authenticationToken = mockk<AuthenticationToken> {
            every { grant } returns mockk {
                every { clientId } returns CLIENT_ID
                every { userId } returns null
                every { clientScopes } returns emptyList()
            }
        }
        stubToken(authenticationToken)
        coJustRun {
            dpopManager.validateAccessTokenBinding(
                token = authenticationToken,
                presentedWithDpopScheme = true,
                proofs = listOf(PROOF),
                boundRequest = DpopBoundRequest(
                    method = "GET",
                    uri = URI.create(USERINFO_PATH),
                    accessToken = ENCODED_TOKEN
                )
            )
        }
        coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns mockk()
        val request = HttpRequest.GET<Any>(USERINFO_PATH)
            .header("Authorization", "DPoP $ENCODED_TOKEN")
            .header("DPoP", PROOF)

        val authentication = validator().validateToken(ENCODED_TOKEN, request).asFlow().single()

        assertSame(authenticationToken, (authentication as ClientAuthentication).authenticationToken)
    }

    @Test
    fun `validateToken - Refuse with 401 a token presented as a bearer token that its binding refuses`() = runTest {
        val authenticationToken = mockk<AuthenticationToken>()
        stubToken(authenticationToken)
        coEvery {
            dpopManager.validateAccessTokenBinding(
                token = authenticationToken,
                presentedWithDpopScheme = false,
                proofs = emptyList(),
                boundRequest = any()
            )
        } throws oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.bound_token_as_bearer")

        // No client is stubbed: reaching the assertion proves the binding refused before the client was read.
        val exception = assertThrows<LocalizedHttpException> {
            validator().validateToken(ENCODED_TOKEN, bearerRequest()).awaitFirstOrNull()
        }
        assertEquals(UNAUTHORIZED, exception.status)
        assertEquals("dpop.bound_token_as_bearer", exception.detailsId)
    }

    @Test
    fun `validateToken - Authenticate nothing without a request`() = runTest {
        // No stub: reaching the assertion is the proof that no token was decoded.
        assertNull(validator().validateToken(ENCODED_TOKEN, null).awaitFirstOrNull())
    }

    private companion object {
        const val ENCODED_TOKEN = "encoded-access-token"
        const val CLIENT_ID = "client-app"
        const val PROOF = "a-proof"
        const val USERINFO_PATH = "/api/openid/userinfo"
    }
}
