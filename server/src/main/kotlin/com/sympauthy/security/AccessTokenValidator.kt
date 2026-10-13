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
 * - to retrieve the token and scopes it is associated to.
 * - to check the request presents the token the way its binding to a DPoP key requires.
 * - to retrieve the client the token was issued to, which the configuration must still declare.
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
                // The header lookup carries no nullability annotation, so a request carrying no proof is not
                // trusted to answer an empty list.
                proofs = request.headers.getAll(DPOP_HEADER) ?: emptyList(),
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

        // A token does not outlive its client, and is refused rather than revoked. See `docs/design/security.md`.
        val client = clientManager.findClientByIdOrNull(authenticationToken.clientId)
            ?: throw httpExceptionOf(
                UNAUTHORIZED,
                "token.client_disabled",
                "description.token.client_disabled",
                "clientId" to authenticationToken.clientId
            )

        val authentication = if (authenticationToken.userId != null) {
            val consentedScopes = authenticationToken.consentedScopes.mapNotNull {
                scopeManager.find(it)
            }
            val grantedScopes = authenticationToken.grantedScopes.mapNotNull {
                scopeManager.find(it)
            }
            UserAuthentication(
                authenticationToken = authenticationToken,
                client = client,
                consentedScopes = consentedScopes,
                grantedScopes = grantedScopes
            )
        } else {
            val scopes = authenticationToken.clientScopes.mapNotNull {
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
