package com.sympauthy.security

import com.sympauthy.api.exception.AccessTokenChallengeException
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.toAccessTokenChallengeException
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager.Companion.DPOP_HEADER
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InvalidDpopProof
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InvalidToken
import com.sympauthy.exception.toOutcome
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.TokenMetrics
import com.sympauthy.security.AccessTokenScheme.BEARER
import com.sympauthy.security.AccessTokenScheme.DPOP
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.validator.TokenValidator
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactive.publish
import org.reactivestreams.Publisher

/**
 * [TokenValidator] that validates token we have issued with this authorization server.
 *
 * To authorize the request, we need:
 * - to decode the token.
 * - to validate the token signature against our [ACCESS_KEY] signature key.
 * - to check the token is not expired.
 * - to validate the token is an access token.
 * - to retrieve the token, the grant it was issued under and the scopes that grant holds.
 * - to check the request presents the token the way its binding to a DPoP key requires.
 * - to retrieve the client the grant of the token was opened for, which the configuration must still declare.
 *
 * Depending on whether the token is associated with a user (authorization_code/refresh_token flows)
 * or a client only (client_credentials flow), we return a [UserAuthentication] or [ClientAuthentication].
 *
 * Every refusal is a `401` challenging the token under the scheme it was presented with: `invalid_dpop_proof`
 * where its DPoP proof is what refused it, and `invalid_token` for every other reason. The challenge is sent
 * only where the route the request reached accepts an access token.
 */
@Singleton
class AccessTokenValidator(
    @Inject private val clientManager: ClientManager,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val dpopManager: DpopManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val dpopTokenReader: DpopTokenReader,
    @Inject private val tokenMetrics: TokenMetrics
) : TokenValidator<HttpRequest<*>> {

    override fun validateToken(token: String, request: HttpRequest<*>?): Publisher<Authentication> = publish {
        // How the request presented the token is what a binding is checked against, so a token handed over
        // without one authenticates nothing.
        if (request == null) {
            return@publish
        }

        val start = System.nanoTime()
        val scheme = if (dpopTokenReader.isPresentedWith(request, token)) DPOP else BEARER
        val authentication = try {
            authenticate(token, request, scheme)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // A refusal is recorded without a client: the client is read from the configuration last.
            tokenMetrics.recordValidation(scheme.metricName, null, System.nanoTime() - start, failure.toOutcome())
            throw failure
        }
        val clientId = when (authentication) {
            is UserAuthentication -> authentication.authenticationToken.grant.clientId
            is ClientAuthentication -> authentication.authenticationToken.grant.clientId
            else -> null
        }
        tokenMetrics.recordValidation(scheme.metricName, clientId, System.nanoTime() - start, Outcome.Success)
        send(authentication)
    }

    /**
     * The authentication [token], presented under [scheme], stands for.
     */
    private suspend fun authenticate(
        token: String,
        request: HttpRequest<*>,
        scheme: AccessTokenScheme
    ): Authentication {
        val decodedToken = try {
            jwtManager.decodeAndVerify(ACCESS_KEY, token)
        } catch (e: InvalidJwtException) {
            throw e.toAccessTokenChallengeException(scheme, listOf(InvalidToken))
        }

        val authenticationToken = try {
            val authenticationToken = tokenManager.getAuthenticationToken(decodedToken)
            dpopManager.validateAccessTokenBinding(
                token = authenticationToken,
                presentedWithDpopScheme = scheme == DPOP,
                proofs = request.headers.getAll(DPOP_HEADER),
                boundRequest = DpopBoundRequest(
                    method = request.method.name,
                    uri = request.uri,
                    accessToken = token
                )
            )
            authenticationToken
        } catch (e: OAuth2Exception) {
            // The managers name a refusal as the token endpoint answers it — `invalid_grant` for a revoked grant —
            // and a resource answers every one of them but a refused proof with `invalid_token`.
            val challenge = if (e.errorCode == INVALID_DPOP_PROOF) InvalidDpopProof else InvalidToken
            throw e.toAccessTokenChallengeException(scheme, listOf(challenge))
        }

        // A grant does not outlive its client, and is refused rather than revoked. See `docs/design/grants.md`.
        val grant = authenticationToken.grant
        val client = clientManager.findClientByIdOrNull(grant.clientId)
            ?: throw AccessTokenChallengeException(
                recoverable = false,
                detailsId = "grant.client_disabled",
                descriptionId = "description.grant.client_disabled",
                values = mapOf("clientId" to grant.clientId),
                scheme = scheme,
                challenges = listOf(InvalidToken)
            )

        return if (grant.userId != null) {
            val consentedScopes = grant.consentedScopes.mapNotNull {
                scopeManager.find(it)
            }
            val grantedScopes = grant.grantedScopes.mapNotNull {
                scopeManager.find(it)
            }
            UserAuthentication(
                authenticationToken = authenticationToken,
                client = client,
                consentedScopes = consentedScopes,
                grantedScopes = grantedScopes
            )
        } else {
            val scopes = grant.clientScopes.mapNotNull {
                scopeManager.find(it)
            }
            ClientAuthentication(
                authenticationToken = authenticationToken,
                client = client,
                scopes = scopes
            )
        }
    }

    /**
     * The scheme a check is recorded under.
     */
    private val AccessTokenScheme.metricName: String
        get() = name.lowercase()
}
