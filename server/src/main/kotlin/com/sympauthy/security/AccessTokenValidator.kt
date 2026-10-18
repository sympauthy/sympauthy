package com.sympauthy.security

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.exception.toHttpException
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager.Companion.DPOP_HEADER
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.validator.TokenValidator
import jakarta.inject.Inject
import jakarta.inject.Singleton
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
 */
@Singleton
class AccessTokenValidator(
    @Inject private val clientManager: ClientManager,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val dpopManager: DpopManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val dpopTokenReader: DpopTokenReader
) : TokenValidator<HttpRequest<*>> {

    override fun validateToken(token: String, request: HttpRequest<*>?): Publisher<Authentication> = publish {
        // How the request presented the token is what a binding is checked against, so a token handed over
        // without one authenticates nothing.
        if (request == null) {
            return@publish
        }

        val decodedToken = try {
            jwtManager.decodeAndVerify(ACCESS_KEY, token)
        } catch (e: InvalidJwtException) {
            throw e.toHttpException(UNAUTHORIZED)
        }

        val authenticationToken = try {
            val authenticationToken = tokenManager.getAuthenticationToken(decodedToken)
            dpopManager.validateAccessTokenBinding(
                token = authenticationToken,
                presentedWithDpopScheme = dpopTokenReader.isPresentedWith(request, token),
                proofs = request.headers.getAll(DPOP_HEADER),
                boundRequest = DpopBoundRequest(
                    method = request.method.name,
                    uri = request.uri,
                    accessToken = token
                )
            )
            authenticationToken
        } catch (e: OAuth2Exception) {
            throw e.toHttpException(UNAUTHORIZED)
        }

        // A grant does not outlive its client, and is refused rather than revoked. See `docs/design/grants.md`.
        val grant = authenticationToken.grant
        val client = clientManager.findClientByIdOrNull(grant.clientId)
            ?: throw httpExceptionOf(
                UNAUTHORIZED,
                "grant.client_disabled",
                "description.grant.client_disabled",
                "clientId" to grant.clientId
            )

        val authentication = if (grant.userId != null) {
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
        send(authentication)
    }
}
