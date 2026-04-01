package com.sympauthy.security

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.toHttpException
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.exception.LocalizedException
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
 *
 * Depending on whether the token is associated with a user (authorization_code/refresh_token flows)
 * or a client only (client_credentials flow), we return a [UserAuthentication] or [ClientAuthentication].
 */
@Singleton
class AccessTokenValidator<T>(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val jwtManager: JwtManager
) : TokenValidator<T> {

    override fun validateToken(token: String, request: T): Publisher<Authentication> = publish {
        val decodedToken = try {
            jwtManager.decodeAndVerify(ACCESS_KEY, token)
        } catch (e: LocalizedException) {
            throw e.toHttpException(UNAUTHORIZED)
        }

        val authenticationToken = try {
            tokenManager.getAuthenticationToken(decodedToken)
        } catch (e: OAuth2Exception) {
            throw e.toHttpException(UNAUTHORIZED)
        }

        val authentication = if (authenticationToken.userId != null) {
            val consentedScopes = authenticationToken.consentedScopes.mapNotNull {
                scopeManager.find(it)
            }
            val grantedScopes = authenticationToken.grantedScopes.mapNotNull {
                scopeManager.find(it)
            }
            UserAuthentication(
                authenticationToken = authenticationToken,
                consentedScopes = consentedScopes,
                grantedScopes = grantedScopes
            )
        } else {
            val scopes = authenticationToken.clientScopes.mapNotNull {
                scopeManager.find(it)
            }
            ClientAuthentication(
                authenticationToken = authenticationToken,
                scopes = scopes
            )
        }
        send(authentication)
    }
}
