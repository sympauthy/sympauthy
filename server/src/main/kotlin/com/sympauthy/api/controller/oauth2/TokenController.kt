package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.toOauth2Exception
import com.sympauthy.api.resource.oauth2.TokenResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.oauth2.AccessTokenGenerator
import com.sympauthy.business.manager.auth.oauth2.PkceManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_GRANT_TYPE
import com.sympauthy.util.nullIfBlank
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@Controller(OAUTH2_TOKEN_ENDPOINT)
class TokenController(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val authorizeFlowManager: AuthorizationFlowManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val accessTokenGenerator: AccessTokenGenerator,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val clientAuthenticationUtil: ClientAuthenticationUtil,
    @Inject private val pkceManager: PkceManager
) {

    @Operation(
        description = """
The token endpoint is used by the client to obtain an access token by presenting its authorization grant or refresh token.

Client authentication is supported via:
- HTTP Basic Auth (`Authorization: Basic <base64(client_id:client_secret)>`)
- Form body parameters (`client_id` / `client_secret`)
""",
        tags = ["oauth2"],
        parameters = [
            Parameter(
                name = "grant_type",
                description = "The type of grant being requested.",
                schema = Schema(
                    type = "string",
                    allowableValues = ["authorization_code", "refresh_token", "client_credentials"]
                )
            ),
            Parameter(
                name = "code",
                description = "The authorization code received from the authorization endpoint. Required for the `authorization_code` grant.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "redirect_uri",
                description = "The redirect URI used in the initial authorization request. Required for the `authorization_code` grant.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "refresh_token",
                description = "The refresh token. Required for the `refresh_token` grant.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "scope",
                description = "The scope of the access request. Optional for the `client_credentials` grant.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "client_id",
                description = "The client identifier. Used when client credentials are passed in the request body instead of via Basic Auth.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "client_secret",
                description = "The client secret. Used when client credentials are passed in the request body instead of via Basic Auth.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "code_verifier",
                description = "The PKCE code verifier (RFC 7636). Required when a code_challenge was sent during authorization.",
                schema = Schema(type = "string")
            )
        ],
        externalDocs = ExternalDocumentation(
            description = "Token Endpoint specification",
            url = "https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-3.2"
        )
    )
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Post(consumes = [APPLICATION_FORM_URLENCODED])
    suspend fun getTokens(
        request: HttpRequest<*>,
        @Part("grant_type") grantType: String?,
        @Part(CODE_PARAM) code: String?,
        @Part("redirect_uri") redirectUri: String?,
        @Part(REFRESH_TOKEN_PARAM) refreshToken: String?,
        @Part("scope") scope: String?,
        @Part("client_id") clientId: String?,
        @Part("client_secret") clientSecret: String?,
        @Part("code_verifier") codeVerifier: String?,
    ): TokenResource {
        return when (grantType) {
            "authorization_code" -> {
                val client = clientAuthenticationUtil.resolveClientAllowingPublic(
                    request, clientId, clientSecret
                )
                getTokensUsingAuthorizationCode(
                    code = code,
                    redirectUri = redirectUri,
                    codeVerifier = codeVerifier
                )
            }

            "refresh_token" -> {
                val client = clientAuthenticationUtil.resolveClientAllowingPublic(request, clientId, clientSecret)
                getTokensUsingRefreshToken(
                    client = client,
                    encodedRefreshToken = refreshToken
                )
            }

            "client_credentials" -> {
                val client = clientAuthenticationUtil.resolveClient(request, clientId, clientSecret)
                getTokensUsingClientCredentials(
                    client = client,
                    scope = scope
                )
            }

            else -> throw oauth2ExceptionOf(
                UNSUPPORTED_GRANT_TYPE, "token.unsupported_grant_type",
                "grantType" to (grantType ?: "")
            )
        }
    }

    private suspend fun getTokensUsingAuthorizationCode(
        code: String?,
        redirectUri: String?,
        codeVerifier: String?
    ): TokenResource {
        if (code.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.missing_param", "param" to CODE_PARAM)
        }

        // TODO: Should we move the logic inside the WebAuthorizationFlowManager?
        val attempt = authorizeAttemptManager.findByCodeOrNull(code)
        val completedAttempt = authorizeFlowManager.checkCanIssueToken(attempt)
        if (completedAttempt.redirectUri != redirectUri) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.non_matching_redirect_uri")
        }

        // Verify PKCE before issuing tokens
        try {
            pkceManager.verifyCodeVerifier(
                codeVerifier = codeVerifier,
                codeChallenge = completedAttempt.codeChallenge,
                codeChallengeMethod = completedAttempt.codeChallengeMethod
            )
        } catch (e: BusinessException) {
            throw e.toOauth2Exception(INVALID_GRANT)
        }

        val tokens = tokenManager.generateTokens(completedAttempt)

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
        client: Client,
        encodedRefreshToken: String?
    ): TokenResource {
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

    private suspend fun getTokensUsingClientCredentials(
        client: Client,
        scope: String?
    ): TokenResource {
        val requestedScopes = scopeManager.parseRequestedScopes(client, scope)
        val scopeStrings = requestedScopes.map { it.scope }

        val accessToken = accessTokenGenerator.generateAccessTokenForClient(
            clientId = client.id,
            scopes = scopeStrings
        )

        return TokenResource(
            accessToken = accessToken.token,
            tokenType = "bearer",
            expiredIn = getExpiredIn(accessToken),
            scope = getScope(accessToken),
            refreshToken = null,
            idToken = null
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
