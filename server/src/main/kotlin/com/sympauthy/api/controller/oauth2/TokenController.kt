package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.controller.oauth2.util.CLIENT_ID_PARAMETER_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.CLIENT_SECRET_PARAMETER_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.controller.oauth2.util.INVALID_REQUEST_BODY_TOO_LARGE_DESCRIPTION
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.toOAuth2Exception
import com.sympauthy.api.resource.oauth2.TokenResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.ClientScopeGrantingManager
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.auth.oauth2.PkceManager
import com.sympauthy.business.manager.auth.oauth2.TokenExchangeManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.DpopProof
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.*
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.nullIfBlank
import com.sympauthy.util.enumValueOfWireNameOrNull
import com.sympauthy.util.wireName
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType.APPLICATION_FORM_URLENCODED
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import java.time.Duration

@Controller(OAUTH2_TOKEN_ENDPOINT)
class TokenController(
    @Inject private val authorizationCodeManager: AuthorizationCodeManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val clientAuthenticationUtil: ClientAuthenticationUtil,
    @Inject private val pkceManager: PkceManager,
    @Inject private val clientScopeGrantingManager: ClientScopeGrantingManager,
    @Inject private val tokenExchangeManager: TokenExchangeManager,
    @Inject private val dpopManager: DpopManager,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    @Operation(
        description = "Exchange a grant for tokens, OAuth 2.1 §3.2. A confidential client authenticates with " +
            "HTTP Basic authentication or with `client_id` and `client_secret` in the body. A request carrying " +
            "a DPoP proof in the `DPoP` header, RFC 9449 §4, is issued tokens bound to the key of that proof.",
        tags = ["oauth2"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Tokens issued to the client.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "- `invalid_request`: `grant_type` is missing; `code` is missing on the " +
                    "`authorization_code` grant, or `refresh_token` on the `refresh_token` grant; on the token " +
                    "exchange grant, `subject_token`, `subject_token_type` or `requested_subject` is missing, " +
                    "or `subject_token_type` or `requested_token_type` names something other than an access " +
                    "token.\n" +
                    "- `invalid_client`: on the `authorization_code` and `refresh_token` grants, no client " +
                    "credentials are sent, or the ones sent in the body do not authenticate a client.\n" +
                    "- `invalid_grant`: `code` or `refresh_token` is unknown, expired or issued to another " +
                    "client; `code` or `refresh_token` was issued under a grant that was revoked, that " +
                    "`auth.grant.expiration` has ended, or that was opened for an audience its client no " +
                    "longer belongs to; `code` was already exchanged; `code_verifier` " +
                    "does not match the authorization request; the consent of the end-user was revoked, or " +
                    "the user never " +
                    "finished signing up; or `subject_token` is not a valid `client_credentials` access token " +
                    "of this client.\n" +
                    "- `unauthorized_client`: the client is not allowed this `grant_type`.\n" +
                    "- `unsupported_grant_type`: `grant_type` is not supported.\n" +
                    "- `invalid_scope`: on the `client_credentials` grant, `scope` names a scope that is not " +
                    "enabled, belongs to another audience, is not a client scope, or is not allowed to the " +
                    "client.\n" +
                    "- `invalid_target`: on the token exchange grant, `resource` is sent, `requested_subject` " +
                    "names no user, or `audience` names no configured audience.\n" +
                    "- `invalid_dpop_proof`: the `DPoP` header is sent more than once or does not validate, " +
                    "is missing while `auth.token.dpop-required` is true, or is missing or signed with " +
                    "another key on the `refresh_token` grant for a refresh token bound to a key."
            ),
            ApiResponse(
                responseCode = "401",
                description = "- `invalid_client`: the client credentials sent with HTTP Basic authentication " +
                    "do not authenticate a client, or, on the `client_credentials` and token exchange grants, " +
                    "none are sent or the ones sent in the body do not. The `WWW-Authenticate` header names " +
                    "the `Basic` scheme.\n" +
                    "- `access_denied`: on the token exchange grant, the `rules.act-as` rules do not " +
                    "let the client act for `requested_subject`."
            ),
            ApiResponse(responseCode = "413", description = INVALID_REQUEST_BODY_TOO_LARGE_DESCRIPTION)
        ],
        externalDocs = ExternalDocumentation(
            description = "OAuth 2.1, §3.2",
            url = "https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-3.2"
        )
    )
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Post(consumes = [APPLICATION_FORM_URLENCODED])
    suspend fun getTokens(
        request: HttpRequest<*>,
        @Part(GRANT_TYPE_PARAM)
        @Schema(
            name = GRANT_TYPE_PARAM,
            description = "Grant the client presents, OAuth 2.1 §3.2.2.",
            allowableValues = [
                "authorization_code",
                "refresh_token",
                "client_credentials",
                "urn:ietf:params:oauth:grant-type:token-exchange"
            ]
        )
        grantType: String?,
        @Part(CODE_PARAM)
        @Schema(
            name = CODE_PARAM,
            description = "Authorization code the browser brought back to the `redirect_uri` of the " +
                "authorization request. It expires `auth.authorization-code.expiration` after this server " +
                "issued it. It is exchanged once: a later exchange is refused, and one passing every other " +
                "check also revokes every token issued for the code, refreshed ones included. Required for " +
                "the `authorization_code` grant."
        )
        code: String?,
        @Part(REFRESH_TOKEN_PARAM)
        @Schema(
            name = REFRESH_TOKEN_PARAM,
            description = "Refresh token to exchange. Required for the `refresh_token` grant."
        )
        refreshToken: String?,
        @Part("scope")
        @Schema(
            name = "scope",
            description = "Scopes the client asks for, separated by spaces. Read on the `client_credentials` " +
                "grant. When absent, the access token carries no scope."
        )
        scope: String?,
        @Part("client_id")
        @Schema(
            name = "client_id",
            description = CLIENT_ID_PARAMETER_DESCRIPTION + " A public client sends it alone, on the " +
                "`authorization_code` and `refresh_token` grants."
        )
        clientId: String?,
        @Part("client_secret")
        @Schema(name = "client_secret", description = CLIENT_SECRET_PARAMETER_DESCRIPTION)
        clientSecret: String?,
        @Part("code_verifier")
        @Schema(
            name = "code_verifier",
            description = "PKCE code verifier the `code_challenge` of the authorization request was derived " +
                "from, RFC 7636 §4.5. Required for the `authorization_code` grant."
        )
        codeVerifier: String?,
        @Part(SUBJECT_TOKEN_PARAM)
        @Schema(
            name = SUBJECT_TOKEN_PARAM,
            description = "Access token the client itself obtained with the `client_credentials` grant, " +
                "RFC 8693 §2.1. The issued token names the client as its actor. Required for the token " +
                "exchange grant."
        )
        subjectToken: String? = null,
        @Part(SUBJECT_TOKEN_TYPE_PARAM)
        @Schema(
            name = SUBJECT_TOKEN_TYPE_PARAM,
            description = "Type of `subject_token`, RFC 8693 §2.1. Required for the token exchange grant.",
            allowableValues = ["urn:ietf:params:oauth:token-type:access_token"]
        )
        subjectTokenType: String? = null,
        @Part(REQUESTED_SUBJECT_PARAM)
        @Schema(
            name = REQUESTED_SUBJECT_PARAM,
            description = "Identifier of the user the client acts for. Required for the token exchange grant."
        )
        requestedSubject: String? = null,
        @Part("requested_token_type")
        @Schema(
            name = "requested_token_type",
            description = "Type of the token the client asks for, RFC 8693 §2.1. Read on the token exchange " +
                "grant. When absent, an access token.",
            allowableValues = ["urn:ietf:params:oauth:token-type:access_token"]
        )
        requestedTokenType: String? = null,
        @Part("resource")
        @Schema(
            name = "resource",
            description = "URL where the client intends to use the token, RFC 8693 §2.1. Not supported: a " +
                "token exchange request carrying it is refused."
        )
        resource: String? = null,
        @Part("audience")
        @Schema(
            name = "audience",
            description = "Audience the token is for, RFC 8693 §2.1, matched against the token audience or " +
                "the identifier of a configured audience. Read on the token exchange grant. When absent, " +
                "the audience the client belongs to."
        )
        audience: String? = null,
    ): TokenResource {
        // Validate DPoP proof if present, enforce if required by config
        val dpopProof = dpopManager.validateDpopProof(
            proofs = request.headers.getAll(DpopManager.DPOP_HEADER),
            boundRequest = DpopBoundRequest(
                method = request.method.name,
                uri = request.uri,
                accessToken = null
            )
        )
        val authConfig = uncheckedAuthConfig.orThrow()
        if (dpopProof == null && authConfig.token.dpopRequired) {
            throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.missing_header")
        }

        return when (enumValueOfWireNameOrNull<GrantType>(grantType)) {
            GrantType.AUTHORIZATION_CODE -> {
                val client = clientAuthenticationUtil.resolveClientAllowingPublic(
                    request, clientId, clientSecret
                )
                checkClientSupportsGrantType(client, GrantType.AUTHORIZATION_CODE)
                getTokensUsingAuthorizationCode(
                    client = client,
                    code = code,
                    codeVerifier = codeVerifier,
                    dpopProof = dpopProof
                )
            }

            GrantType.REFRESH_TOKEN -> {
                val client = clientAuthenticationUtil.resolveClientAllowingPublic(request, clientId, clientSecret)
                checkClientSupportsGrantType(client, GrantType.REFRESH_TOKEN)
                getTokensUsingRefreshToken(
                    client = client,
                    encodedRefreshToken = refreshToken,
                    dpopProof = dpopProof
                )
            }

            GrantType.CLIENT_CREDENTIALS -> {
                val client = clientAuthenticationUtil.resolveClient(request, clientId, clientSecret)
                checkClientSupportsGrantType(client, GrantType.CLIENT_CREDENTIALS)
                getTokensUsingClientCredentials(
                    client = client,
                    scope = scope,
                    dpopProof = dpopProof
                )
            }

            GrantType.TOKEN_EXCHANGE -> {
                val client = clientAuthenticationUtil.resolveClient(request, clientId, clientSecret)
                checkClientSupportsGrantType(client, GrantType.TOKEN_EXCHANGE)
                getTokensUsingTokenExchange(
                    client = client,
                    subjectToken = subjectToken,
                    subjectTokenType = subjectTokenType,
                    requestedSubject = requestedSubject,
                    requestedTokenType = requestedTokenType,
                    resource = resource,
                    audience = audience,
                    dpopProof = dpopProof
                )
            }

            null -> throw if (grantType.isNullOrBlank()) {
                oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to GRANT_TYPE_PARAM)
            } else {
                oauth2ExceptionOf(UNSUPPORTED_GRANT_TYPE, "token.unsupported_grant_type", "grantType" to grantType)
            }
        }
    }

    private suspend fun getTokensUsingAuthorizationCode(
        client: Client,
        code: String?,
        codeVerifier: String?,
        dpopProof: DpopProof?
    ): TokenResource {
        if (code.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to CODE_PARAM)
        }

        val (authorizationCode, grant) = authorizationCodeManager.checkCanExchange(
            authorizationCodeManager.findByCodeOrNull(code), client
        )

        // Verify PKCE before issuing tokens
        try {
            pkceManager.verifyCodeVerifier(
                codeVerifier = codeVerifier,
                codeChallenge = authorizationCode.codeChallenge,
                codeChallengeMethod = authorizationCode.codeChallengeMethod
            )
        } catch (e: BusinessException) {
            throw e.toOAuth2Exception(INVALID_GRANT)
        }

        val tokens = tokenManager.generateTokensForUser(authorizationCode, grant, client, dpopJkt = dpopProof?.jkt)
        val tokenType = if (dpopProof != null) TOKEN_TYPE_DPOP else TOKEN_TYPE_BEARER

        return TokenResource(
            accessToken = tokens.accessToken.token,
            tokenType = tokenType,
            expiresIn = getExpiresIn(tokens.accessToken),
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
            throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to REFRESH_TOKEN_PARAM)
        }
        val tokens = tokenManager.refreshToken(client, encodedRefreshToken, dpopJkt = dpopProof?.jkt)
        val accessToken = tokens.accessToken
        val tokenType = if (dpopProof != null) TOKEN_TYPE_DPOP else TOKEN_TYPE_BEARER
        return TokenResource(
            accessToken = accessToken.token,
            tokenType = tokenType,
            expiresIn = getExpiresIn(accessToken),
            scope = getScope(accessToken),
            refreshToken = tokens.refreshToken?.token ?: encodedRefreshToken,
            idToken = tokens.idToken?.token
        )
    }

    private suspend fun getTokensUsingClientCredentials(
        client: Client,
        scope: String?,
        dpopProof: DpopProof?
    ): TokenResource {
        // Every BusinessException this call throws refuses a scope the client asked for, so each one is
        // invalid_scope. A server failure thrown as one would reach the client as invalid_scope too; a scopes
        // configuration that failed to load throws a LocalizedException instead, and still answers 500.
        val requestedScopes = try {
            scopeManager.parseRequestedClientScopes(client, scope)
        } catch (e: BusinessException) {
            throw e.toOAuth2Exception(INVALID_SCOPE)
        }
        val grantResult = clientScopeGrantingManager.grantClientScopes(client, requestedScopes)
        val scopeStrings = grantResult.grantedScopes.map { it.scope }

        val accessToken = tokenManager.generateAccessTokenForClient(
            client = client,
            clientScopes = scopeStrings,
            dpopJkt = dpopProof?.jkt
        )
        val tokenType = if (dpopProof != null) TOKEN_TYPE_DPOP else TOKEN_TYPE_BEARER

        return TokenResource(
            accessToken = accessToken.token,
            tokenType = tokenType,
            expiresIn = getExpiresIn(accessToken),
            scope = getScope(accessToken),
            refreshToken = null,
            idToken = null
        )
    }

    private suspend fun getTokensUsingTokenExchange(
        client: Client,
        subjectToken: String?,
        subjectTokenType: String?,
        requestedSubject: String?,
        requestedTokenType: String?,
        resource: String?,
        audience: String?,
        dpopProof: DpopProof?
    ): TokenResource {
        if (subjectToken.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to SUBJECT_TOKEN_PARAM)
        }
        if (subjectTokenType.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to SUBJECT_TOKEN_TYPE_PARAM)
        }
        if (requestedSubject.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to REQUESTED_SUBJECT_PARAM)
        }
        // requested_token_type is optional, but when present we only support issuing access tokens.
        if (requestedTokenType != null && requestedTokenType != TokenExchangeManager.ACCESS_TOKEN_TYPE) {
            throw oauth2ExceptionOf(
                INVALID_REQUEST, "token_exchange.unsupported_requested_token_type", "type" to requestedTokenType
            )
        }

        val accessToken = tokenExchangeManager.exchangeForActAsToken(
            actingClient = client,
            subjectToken = subjectToken,
            subjectTokenType = subjectTokenType,
            requestedSubject = requestedSubject,
            resource = resource,
            audience = audience,
            dpopJkt = dpopProof?.jkt
        )
        val tokenType = if (dpopProof != null) TOKEN_TYPE_DPOP else TOKEN_TYPE_BEARER

        return TokenResource(
            accessToken = accessToken.token,
            tokenType = tokenType,
            expiresIn = getExpiresIn(accessToken),
            scope = getScope(accessToken),
            refreshToken = null,
            idToken = null,
            issuedTokenType = TokenExchangeManager.ACCESS_TOKEN_TYPE
        )
    }

    private fun checkClientSupportsGrantType(client: Client, grantType: GrantType) {
        if (!client.supportsGrantType(grantType)) {
            throw oauth2ExceptionOf(
                OAuth2ErrorCode.UNAUTHORIZED_CLIENT,
                "token.unauthorized_grant_type",
                "description.token.unauthorized_grant_type",
                "grantType" to grantType.wireName
            )
        }
    }

    private fun getScope(accessToken: EncodedAuthenticationToken): String? {
        return accessToken.scopes
            .joinToString(" ")
            .nullIfBlank()
    }

    /**
     * Returns the lifetime in seconds of the [accessToken], or null if it never expires.
     *
     * The lifetime is measured from the issue date of the token rather than from now, so that the
     * returned value is exactly the configured lifetime instead of being shortened by the time spent
     * handling the request.
     */
    private fun getExpiresIn(accessToken: EncodedAuthenticationToken): Int? {
        return accessToken.expirationDate?.let {
            Duration.between(accessToken.issueDate, it).toSeconds().toInt()
        }
    }

    companion object {
        const val OAUTH2_TOKEN_ENDPOINT = "/api/oauth2/token"
        const val GRANT_TYPE_PARAM = "grant_type"
        const val CODE_PARAM = "code"
        const val REFRESH_TOKEN_PARAM = "refresh_token"
        const val SUBJECT_TOKEN_PARAM = "subject_token"
        const val SUBJECT_TOKEN_TYPE_PARAM = "subject_token_type"
        const val REQUESTED_SUBJECT_PARAM = "requested_subject"
        const val TOKEN_TYPE_BEARER = "bearer"
        const val TOKEN_TYPE_DPOP = "DPoP"
    }
}
