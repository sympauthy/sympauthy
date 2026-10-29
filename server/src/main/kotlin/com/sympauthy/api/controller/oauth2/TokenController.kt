package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.OAUTH2_SURFACE
import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.controller.oauth2.util.CLIENT_ID_PARAMETER_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.CLIENT_SECRET_PARAMETER_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.CLIENT_SECRET_SENT_TWICE_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.controller.oauth2.util.INVALID_REQUEST_BODY_TOO_LARGE_DESCRIPTION
import com.sympauthy.api.controller.oauth2.util.getRequiredParam
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.mapper.TokenResourceMapper
import com.sympauthy.api.resource.oauth2.TokenResource
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.token.TokenRequestManager
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.DpopBoundRequest
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.*
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.enumValueOfWireNameOrNull
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

@Controller(OAUTH2_TOKEN_ENDPOINT)
class TokenController(
    @Inject private val clientAuthenticationUtil: ClientAuthenticationUtil,
    @Inject private val tokenRequestManager: TokenRequestManager,
    @Inject private val tokenResourceMapper: TokenResourceMapper,
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
                description = "- `invalid_request`: $CLIENT_SECRET_SENT_TWICE_DESCRIPTION; `grant_type` is " +
                    "missing; `code` is missing on the `authorization_code` grant, or `refresh_token` on the " +
                    "`refresh_token` grant; on the token exchange grant, `subject_token`, `subject_token_type` " +
                    "or `requested_subject` is missing, or `subject_token_type` or `requested_token_type` names " +
                    "something other than an access token; or the `rules.act-as` rules do not let the client " +
                    "act for `requested_subject`.\n" +
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
                    "the `Basic` scheme."
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
        @Part(REQUESTED_TOKEN_TYPE_PARAM)
        @Schema(
            name = REQUESTED_TOKEN_TYPE_PARAM,
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

        val parsedGrantType = parseGrantType(grantType)
        val client = if (parsedGrantType.publicClientAllowed) {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, clientId, clientSecret)
        } else {
            clientAuthenticationUtil.resolveClient(request, clientId, clientSecret)
        }
        val issuance = tokenRequestManager.issueTokens(client, parsedGrantType, dpopProof?.jkt) {
            when (parsedGrantType) {
                GrantType.AUTHORIZATION_CODE -> TokenRequest.AuthorizationCode(
                    code = getRequiredParam(code, CODE_PARAM),
                    codeVerifier = codeVerifier
                )

                GrantType.REFRESH_TOKEN -> TokenRequest.RefreshToken(
                    refreshToken = getRequiredParam(refreshToken, REFRESH_TOKEN_PARAM)
                )

                GrantType.CLIENT_CREDENTIALS -> TokenRequest.ClientCredentials(scope = scope)

                GrantType.TOKEN_EXCHANGE -> readTokenExchangeRequest(
                    subjectToken = subjectToken,
                    subjectTokenType = subjectTokenType,
                    requestedSubject = requestedSubject,
                    requestedTokenType = requestedTokenType,
                    resource = resource,
                    audience = audience
                )
            }
        }
        return tokenResourceMapper.toResource(issuance, dpopProof?.jkt)
    }

    /**
     * The grant type [grantType] names.
     *
     * Throws an `OAuth2Exception` carrying `invalid_request` and `token.missing_param` where [grantType] is
     * missing, and one carrying `unsupported_grant_type` where it names no grant type this server supports.
     */
    private fun parseGrantType(grantType: String?): GrantType {
        val requiredGrantType = getRequiredParam(grantType, GRANT_TYPE_PARAM)
        return enumValueOfWireNameOrNull<GrantType>(requiredGrantType)
            ?: throw oauth2ExceptionOf(
                UNSUPPORTED_GRANT_TYPE, "token.unsupported_grant_type", "grantType" to requiredGrantType
            )
    }

    /**
     * The token exchange a request carrying these parameters asks for.
     *
     * Throws an `OAuth2Exception` carrying `invalid_request` and `token.missing_param` where [subjectToken],
     * [subjectTokenType] or [requestedSubject] is missing, then one carrying `invalid_request` and
     * `token_exchange.unsupported_requested_token_type` where [requestedTokenType] names anything but an
     * access token, then one carrying `invalid_request` and `token_exchange.unsupported_subject_token_type`
     * where [subjectTokenType] does, and one carrying `invalid_target` and `token_exchange.unsupported_resource`
     * where [resource] is sent at all.
     *
     * A `resource` names a URL the server would map to a policy, which this server does not implement, so it is
     * refused rather than ignored: ignoring it would change where the client believes the token is usable.
     */
    private fun readTokenExchangeRequest(
        subjectToken: String?,
        subjectTokenType: String?,
        requestedSubject: String?,
        requestedTokenType: String?,
        resource: String?,
        audience: String?
    ): TokenRequest.TokenExchange {
        val requiredSubjectToken = getRequiredParam(subjectToken, SUBJECT_TOKEN_PARAM)
        val requiredSubjectTokenType = getRequiredParam(subjectTokenType, SUBJECT_TOKEN_TYPE_PARAM)
        val requiredRequestedSubject = getRequiredParam(requestedSubject, REQUESTED_SUBJECT_PARAM)
        if (requestedTokenType != null && requestedTokenType != TokenRequest.TokenExchange.ACCESS_TOKEN_TYPE) {
            throw oauth2ExceptionOf(
                INVALID_REQUEST, "token_exchange.unsupported_requested_token_type", "type" to requestedTokenType
            )
        }
        if (requiredSubjectTokenType != TokenRequest.TokenExchange.ACCESS_TOKEN_TYPE) {
            throw oauth2ExceptionOf(
                INVALID_REQUEST, "token_exchange.unsupported_subject_token_type", "type" to requiredSubjectTokenType
            )
        }
        if (!resource.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_TARGET, "token_exchange.unsupported_resource")
        }
        return TokenRequest.TokenExchange(
            subjectToken = requiredSubjectToken,
            requestedSubject = requiredRequestedSubject,
            audience = audience
        )
    }

    companion object {
        const val OAUTH2_TOKEN_ENDPOINT = "$OAUTH2_SURFACE/token"
        const val GRANT_TYPE_PARAM = "grant_type"
        const val CODE_PARAM = "code"
        const val REFRESH_TOKEN_PARAM = "refresh_token"
        const val SUBJECT_TOKEN_PARAM = "subject_token"
        const val SUBJECT_TOKEN_TYPE_PARAM = "subject_token_type"
        const val REQUESTED_SUBJECT_PARAM = "requested_subject"
        const val REQUESTED_TOKEN_TYPE_PARAM = "requested_token_type"
    }
}
