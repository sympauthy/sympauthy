package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.toOAuth2Exception
import com.sympauthy.api.resource.oauth2.TokenResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.ClientScopeGrantingManager
import com.sympauthy.business.manager.auth.oauth2.AccessTokenGenerator
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.auth.oauth2.PkceManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.DpopProof
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_GRANT_TYPE
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
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
    @Inject private val pkceManager: PkceManager,
    @Inject private val clientScopeGrantingManager: ClientScopeGrantingManager,
    @Inject private val dpopManager: DpopManager,
    @Inject private val uncheckedAuthConfig: AuthConfig
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
        // Validate DPoP proof if present, enforce if required by config
        val dpopProof = dpopManager.validateDpopProof(request)
        val authConfig = uncheckedAuthConfig.orThrow()
        if (dpopProof == null && authConfig.token.dpopRequired) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.missing_header")
        }

        return when (grantType) {
            "authorization_code" -> {
                val client = clientAuthenticationUtil.resolveClientAllowingPublic(
                    request, clientId, clientSecret
                )
                checkClientSupportsGrantType(client, GrantType.AUTHORIZATION_CODE)
                getTokensUsingAuthorizationCode(
                    client = client,
                    code = code,
                    redirectUri = redirectUri,
                    codeVerifier = codeVerifier,
                    dpopProof = dpopProof
                )
            }

            "refresh_token" -> {
                val client = clientAuthenticationUtil.resolveClientAllowingPublic(request, clientId, clientSecret)
                checkClientSupportsGrantType(client, GrantType.REFRESH_TOKEN)
                getTokensUsingRefreshToken(
                    client = client,
                    encodedRefreshToken = refreshToken,
                    dpopProof = dpopProof
                )
            }

            "client_credentials" -> {
                val client = clientAuthenticationUtil.resolveClient(request, clientId, clientSecret)
                checkClientSupportsGrantType(client, GrantType.CLIENT_CREDENTIALS)
                getTokensUsingClientCredentials(
                    client = client,
                    scope = scope,
                    dpopProof = dpopProof
                )
            }

            else -> throw oauth2ExceptionOf(
                UNSUPPORTED_GRANT_TYPE, "token.unsupported_grant_type",
                "grantType" to (grantType ?: "")
            )
        }
    }

    private suspend fun getTokensUsingAuthorizationCode(
        client: Client,
        code: String?,
        redirectUri: String?,
        codeVerifier: String?,
        dpopProof: DpopProof?
    ): TokenResource {
        if (code.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.missing_param", "param" to CODE_PARAM)
        }

        val attempt = authorizeAttemptManager.findByCodeOrNull(code)
        val completedAttempt = try {
            authorizeFlowManager.checkCanIssueToken(attempt, client)
        } catch (e: BusinessException) {
            throw e.toOAuth2Exception(INVALID_GRANT)
        }
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
            throw e.toOAuth2Exception(INVALID_GRANT)
        }

        val tokens = tokenManager.generateTokens(completedAttempt, client, dpopJkt = dpopProof?.jkt)
        val tokenType = if (dpopProof != null) TOKEN_TYPE_DPOP else TOKEN_TYPE_BEARER

        return TokenResource(
            accessToken = tokens.accessToken.token,
            tokenType = tokenType,
            expiredIn = getExpiredIn(tokens.accessToken),
            scope = getScope(tokens.accessToken),
            refreshToken = tokens.refreshToken?.token,
            idToken = tokens.idToken?.token
        )
    }

    private suspend fun getTokensUsingRefreshToken(
        client: Client,
        encodedRefreshToken: String?,
        dpopProof: DpopProof?
    ): TokenResource {
        if (encodedRefreshToken.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.missing_param", "param" to REFRESH_TOKEN_PARAM)
        }
        val tokens = tokenManager.refreshToken(client, encodedRefreshToken, dpopJkt = dpopProof?.jkt)
        val accessToken = tokens.first { it.type == ACCESS }
        val refreshedRefreshToken = tokens.firstOrNull { it.type == REFRESH }
        val tokenType = if (dpopProof != null) TOKEN_TYPE_DPOP else TOKEN_TYPE_BEARER
        return TokenResource(
            accessToken = accessToken.token,
            tokenType = tokenType,
            expiredIn = getExpiredIn(accessToken),
            scope = getScope(accessToken),
            refreshToken = refreshedRefreshToken?.token ?: encodedRefreshToken
        )
    }

    private suspend fun getTokensUsingClientCredentials(
        client: Client,
        scope: String?,
        dpopProof: DpopProof?
    ): TokenResource {
        val requestedScopes = scopeManager.parseRequestedClientScopes(client, scope)
        val grantResult = clientScopeGrantingManager.grantClientScopes(client, requestedScopes)
        val scopeStrings = grantResult.grantedScopes.map { it.scope }

        val accessToken = accessTokenGenerator.generateAccessTokenForClient(
            clientId = client.id,
            clientScopes = scopeStrings,
            dpopJkt = dpopProof?.jkt
        )
        val tokenType = if (dpopProof != null) TOKEN_TYPE_DPOP else TOKEN_TYPE_BEARER

        return TokenResource(
            accessToken = accessToken.token,
            tokenType = tokenType,
            expiredIn = getExpiredIn(accessToken),
            scope = getScope(accessToken),
            refreshToken = null,
            idToken = null
        )
    }

    private fun checkClientSupportsGrantType(client: Client, grantType: GrantType) {
        if (!client.supportsGrantType(grantType)) {
            throw oauth2ExceptionOf(
                OAuth2ErrorCode.UNAUTHORIZED_CLIENT,
                "token.unauthorized_grant_type",
                "description.token.unauthorized_grant_type",
                "grantType" to grantType.value
            )
        }
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
        const val TOKEN_TYPE_BEARER = "bearer"
        const val TOKEN_TYPE_DPOP = "DPoP"
    }
}
