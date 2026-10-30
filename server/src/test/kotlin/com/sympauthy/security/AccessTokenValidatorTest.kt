package com.sympauthy.security

import com.sympauthy.api.controller.OPENID_SURFACE
import com.sympauthy.api.exception.AccessTokenChallengeException
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.token.TokenManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TOKEN
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InvalidDpopProof
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InvalidToken
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.monitoring.telemetry.TokenMetrics
import com.sympauthy.security.AccessTokenScheme.BEARER
import com.sympauthy.security.AccessTokenScheme.DPOP
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import io.micronaut.security.authentication.Authentication
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.currentCoroutineContext
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
import org.reactivestreams.Publisher
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

    /**
     * The real manager rather than a mock: whether a grant was opened for its client is a function of the two
     * alone, so every scenario runs it instead of stubbing an answer to it.
     */
    private val grantManager = GrantManager(
        mockk(), mockk(), mockk(), mockk(), mockk(), TokenMetrics(OpenTelemetry.noop())
    )

    private fun validator() = AccessTokenValidator(
        clientManager, scopeManager, grantManager, tokenManager, dpopManager, jwtManager, DpopTokenReader(),
        TokenMetrics(OpenTelemetry.noop())
    )

    private val client = Client(
        id = CLIENT_ID,
        secret = null,
        audience = Audience(id = AUDIENCE, tokenAudience = AUDIENCE),
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
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

            val exception = assertThrows<AccessTokenChallengeException> {
                validator().validateToken(ENCODED_TOKEN, bearerRequest()).asFlow().single()
            }

            assertEquals(UNAUTHORIZED, exception.status)
            assertEquals("grant.client_disabled", exception.detailsId)
            assertEquals(CLIENT_ID, exception.values["clientId"])
            assertEquals(BEARER, exception.scheme)
            assertEquals(listOf(InvalidToken), exception.challenges)
        }

    @Test
    fun `validateToken - Refuse with 401 a token issued for another audience than its client belongs to`() =
        runTest {
            stubToken(mockk {
                every { grant } returns mockk {
                    every { clientId } returns CLIENT_ID
                    every { audienceId } returns "another-audience"
                }
            })
            stubBindingAccepted()
            coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns client

            val exception = assertThrows<AccessTokenChallengeException> {
                validator().validateToken(ENCODED_TOKEN, bearerRequest()).asFlow().single()
            }

            assertEquals(UNAUTHORIZED, exception.status)
            assertEquals("grant.mismatching_audience", exception.detailsId)
            assertEquals("another-audience", exception.values["grantAudienceId"])
            assertEquals(BEARER, exception.scheme)
            assertEquals(listOf(InvalidToken), exception.challenges)
        }

    @Test
    fun `validateToken - Challenge a token that does not decode with invalid_token`() = runTest {
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, ENCODED_TOKEN) } throws InvalidJwtException("jwt.expired")

        val exception = assertThrows<AccessTokenChallengeException> {
            validator().validateToken(ENCODED_TOKEN, bearerRequest()).awaitFirstOrNull()
        }

        assertEquals(UNAUTHORIZED, exception.status)
        assertEquals("jwt.expired", exception.detailsId)
        assertEquals(BEARER, exception.scheme)
        assertEquals(listOf(InvalidToken), exception.challenges)
    }

    @Test
    fun `validateToken - Carry the client a client_credentials token was issued to`() = runTest {
        stubToken(mockk {
            every { grant } returns mockk {
                every { clientId } returns CLIENT_ID
                every { audienceId } returns AUDIENCE
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
        stubToken(mockk {
            every { grant } returns mockk {
                every { clientId } returns CLIENT_ID
                every { audienceId } returns AUDIENCE
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
    fun `validateToken - Read the token under the context of the request it was called for`() = runTest {
        val requestContext = PropagatedContext.empty().plus(RequestElement)
        val decodedToken = mockk<DecodedJwt>()
        var readUnder: PropagatedContext? = null
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, ENCODED_TOKEN) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } coAnswers {
            readUnder = KotlinCoroutinePropagation.findPropagatedContext(currentCoroutineContext())
            throw oauth2ExceptionOf(INVALID_GRANT, "grant.revoked")
        }

        val publisher = requestContext.propagate<Publisher<Authentication>> {
            validator().validateToken(ENCODED_TOKEN, bearerRequest())
        }
        assertThrows<AccessTokenChallengeException> { publisher.asFlow().single() }

        assertSame(requestContext, readUnder)
    }

    @Test
    fun `validateToken - Check the binding of a token presented under the DPoP scheme against its proof`() = runTest {
        val authenticationToken = mockk<AuthenticationToken> {
            every { grant } returns mockk {
                every { clientId } returns CLIENT_ID
                every { audienceId } returns AUDIENCE
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
        coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns client
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
        } throws oauth2ExceptionOf(INVALID_TOKEN, "dpop.bound_token_as_bearer")

        // No client is stubbed: reaching the assertion proves the binding refused before the client was read.
        val exception = assertThrows<AccessTokenChallengeException> {
            validator().validateToken(ENCODED_TOKEN, bearerRequest()).awaitFirstOrNull()
        }
        assertEquals(UNAUTHORIZED, exception.status)
        assertEquals("dpop.bound_token_as_bearer", exception.detailsId)
        assertEquals(BEARER, exception.scheme)
        assertEquals(listOf(InvalidToken), exception.challenges)
    }

    @Test
    fun `validateToken - Challenge a refused proof with invalid_dpop_proof under the DPoP scheme`() = runTest {
        val authenticationToken = mockk<AuthenticationToken>()
        stubToken(authenticationToken)
        coEvery {
            dpopManager.validateAccessTokenBinding(
                token = authenticationToken,
                presentedWithDpopScheme = true,
                proofs = listOf(PROOF),
                boundRequest = any()
            )
        } throws oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.invalid_htu")
        val request = HttpRequest.GET<Any>(USERINFO_PATH)
            .header("Authorization", "DPoP $ENCODED_TOKEN")
            .header("DPoP", PROOF)

        val exception = assertThrows<AccessTokenChallengeException> {
            validator().validateToken(ENCODED_TOKEN, request).awaitFirstOrNull()
        }

        assertEquals(UNAUTHORIZED, exception.status)
        assertEquals("dpop.invalid_htu", exception.detailsId)
        assertEquals(DPOP, exception.scheme)
        assertEquals(listOf(InvalidDpopProof), exception.challenges)
    }

    @Test
    fun `validateToken - Challenge a token refused by its grant with invalid_token`() = runTest {
        val decodedToken = mockk<DecodedJwt>()
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, ENCODED_TOKEN) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } throws
            oauth2ExceptionOf(INVALID_GRANT, "grant.revoked")

        val exception = assertThrows<AccessTokenChallengeException> {
            validator().validateToken(ENCODED_TOKEN, bearerRequest()).awaitFirstOrNull()
        }

        assertEquals("grant.revoked", exception.detailsId)
        assertEquals(BEARER, exception.scheme)
        assertEquals(listOf(InvalidToken), exception.challenges)
    }

    @Test
    fun `validateToken - Authenticate nothing without a request`() = runTest {
        // No stub: reaching the assertion is the proof that no token was decoded.
        assertNull(validator().validateToken(ENCODED_TOKEN, null).awaitFirstOrNull())
    }

    private object RequestElement : PropagatedContextElement

    private companion object {
        const val ENCODED_TOKEN = "encoded-access-token"
        const val CLIENT_ID = "client-app"
        const val AUDIENCE = "default"
        const val PROOF = "a-proof"
        const val USERINFO_PATH = "$OPENID_SURFACE/userinfo"
    }
}
