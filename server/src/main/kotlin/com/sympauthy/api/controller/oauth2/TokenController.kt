package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.resource.oauth2.TokenResource
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_GRANT_TYPE
import com.sympauthy.security.client
import com.sympauthy.util.nullIfBlank
import io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@Controller(OAUTH2_TOKEN_ENDPOINT)
class TokenController(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val tokenManager: TokenManager
) {

    @Operation(
        tags = ["oauth2"]
    )
    @Secured("ROLE_CLIENT")
    @Post(consumes = [APPLICATION_FORM_URLENCODED])
    suspend fun getTokens(
        authentication: Authentication,
        @Part("grant_type") grantType: String?,
        @Part(CODE_PARAM) code: String?,
        @Part("redirect_uri") redirectUri: String?,
        @Part(REFRESH_TOKEN_PARAM) refreshToken: String?,
    ): TokenResource {
        return when (grantType) {
            "authorization_code" -> getTokensUsingAuthorizationCode(
                code = code,
                redirectUri = redirectUri
            )

            "refresh_token" -> getTokensUsingRefreshToken(
                authentication = authentication,
                encodedRefreshToken = refreshToken
            )

            else -> throw oauth2ExceptionOf(
                UNSUPPORTED_GRANT_TYPE, "token.unsupported_grant_type",
                "grantType" to (grantType ?: "")
            )
        }
    }

    private suspend fun getTokensUsingAuthorizationCode(
        code: String?,
        redirectUri: String?
    ): TokenResource {
        if (code.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.missing_param", "param" to CODE_PARAM)
        }
        val attempt = authorizeAttemptManager.findByCodeOrNull(code) ?: throw oauth2ExceptionOf(
            INVALID_GRANT, "token.expired", "description.oauth2.expired"
        )
        if (attempt.redirectUri != redirectUri) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.non_matching_redirect_uri")
        }

        val tokens = tokenManager.generateTokens(attempt)

        return TokenResource(
            accessToken = tokens.accessToken.token,
            tokenType = "bearer",
            expiredIn = getExpiredIn(tokens.accessToken),
            scope = getScope(tokens.accessToken),
            refreshToken = tokens.refreshToken?.token,
            idToken = tokens.idToken?.token
        )
    }

    private suspend fun getTokensUsingRefreshToken(
        authentication: Authentication,
        encodedRefreshToken: String?
    ): TokenResource {
        val client = authentication.client
        if (encodedRefreshToken.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.missing_param", "param" to REFRESH_TOKEN_PARAM)
        }
        val tokens = tokenManager.refreshToken(client, encodedRefreshToken)
        val accessToken = tokens.first { it.type == ACCESS }
        val refreshedRefreshToken = tokens.firstOrNull { it.type == REFRESH }
        return TokenResource(
            accessToken = accessToken.token,
            tokenType = "bearer",
            expiredIn = getExpiredIn(accessToken),
            scope = getScope(accessToken),
            refreshToken = refreshedRefreshToken?.token ?: encodedRefreshToken
        )
    }

    private fun getScope(accessToken: EncodedAuthenticationToken): String? {
        return accessToken.scopes
            .joinToString(" ")
            .nullIfBlank()
    }

    private fun getExpiredIn(accessToken: EncodedAuthenticationToken): Int? {
        return accessToken.expirationDate?.let {
            Duration.between(
                Instant.now(),
                it.toInstant(ZoneOffset.UTC)
            ).toMillisPart()
        }
    }

    companion object {
        const val OAUTH2_TOKEN_ENDPOINT = "/api/oauth2/token"
        const val CODE_PARAM = "code"
        const val REFRESH_TOKEN_PARAM = "refresh_token"
    }
}
