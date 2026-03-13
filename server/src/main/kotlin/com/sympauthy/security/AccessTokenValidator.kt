package com.sympauthy.security

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.httpExceptionOf
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
import java.util.*

/**
 * [TokenValidator] that validates token we have issued with this authorization server.
 *
 * To authorize the user, we need:
 * - to decode the token.
 * - to validate the token signature against our [ACCESS_KEY] signature key.
 * - to check the token is not expired.
 * - to validate the token is an access token.
 * - to retrieve the user and scope this token is associated to.
 * - to check the subject and the user id matches.
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

        val userId = try {
            UUID.fromString(decodedToken.subject)
        } catch (e: IllegalArgumentException) {
            throw httpExceptionOf(UNAUTHORIZED, "access.invalid_token")
        }

        val authenticationToken = try {
            tokenManager.getAuthenticationToken(decodedToken)
        } catch (e: OAuth2Exception) {
            throw e.toHttpException(UNAUTHORIZED)
        }

        val scopes = authenticationToken.scopes.mapNotNull {
            scopeManager.find(it)
        }

        val authentication = UserAuthentication(
            authenticationToken = authenticationToken,
            scopes = scopes
        )
        send(authentication)
    }
}
