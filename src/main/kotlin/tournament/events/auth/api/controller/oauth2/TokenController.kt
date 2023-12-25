package tournament.events.auth.api.controller.oauth2

import io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import jakarta.inject.Inject
import tournament.events.auth.api.exception.oauth2ExceptionOf
import tournament.events.auth.api.model.oauth2.TokenResource
import tournament.events.auth.business.manager.auth.oauth2.AuthorizeManager
import tournament.events.auth.business.manager.auth.oauth2.TokenManager
import tournament.events.auth.business.model.oauth2.OAuth2ErrorCode.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@Controller("/api/oauth2/token")
class TokenController(
    @Inject private val authorizeManager: AuthorizeManager,
    @Inject private val tokenManager: TokenManager
) {

    @Secured("ROLE_CLIENT")
    @Post(consumes = [APPLICATION_FORM_URLENCODED])
    suspend fun getTokens(
        authentication: Authentication,
        @Part("grant_type") grantType: String?,
        @Part(CODE_PARAM) code: String?,
        @Part("redirect_uri") redirectUri: String?,
        @Part("refresh_token") refreshToken: String?,
    ): TokenResource {
        return when (grantType) {
            "authorization_code" -> getTokensUsingAuthorizationCode(
                code = code,
                redirectUri = redirectUri
            )

            "refresh_token" -> TODO()
            else -> throw oauth2ExceptionOf(
                UNSUPPORTED_GRANT_TYPE, "token.unsupported_grant_type",
                "grantType" to grantType
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
        val attempt = authorizeManager.findByCode(code) ?: throw oauth2ExceptionOf(
            INVALID_GRANT, "token.expired", "description.oauth2.expired"
        )
        if (attempt.redirectUri != redirectUri) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.non_matching_redirect_uri")
        }
        val (accessToken, refreshToken) = tokenManager.generateTokens(attempt)

        return TokenResource(
            accessToken = accessToken.token,
            tokenType = "bearer",
            expiredIn = accessToken.expirationDate?.let {
                Duration.between(
                    Instant.now(),
                    it.toInstant(ZoneOffset.UTC)
                ).toMillisPart()
            },
            refreshToken = refreshToken?.token
        )
    }

    private suspend fun getTokensUsingRefreshToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String?
    ): TokenResource {
        TODO()
    }

    companion object {
        const val CODE_PARAM = "code"
    }
}
