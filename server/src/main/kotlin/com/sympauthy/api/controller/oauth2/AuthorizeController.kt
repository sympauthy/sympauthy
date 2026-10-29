package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.controller.oauth2.AuthorizeController.Companion.OAUTH2_AUTHORIZE_ENDPOINT
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_RESPONSE_TYPE
import com.sympauthy.business.model.oauth2.ResponseType
import com.sympauthy.business.model.oauth2.UncheckedAuthorizationRequest
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.util.enumValueOfWireNameOrNull
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Controller(OAUTH2_AUTHORIZE_ENDPOINT)
@Secured(IS_ANONYMOUS)
open class AuthorizeController(
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = "Start the flow answering an authorization request, OAuth 2.1 §4.1.1. Once the end-user " +
            "completes the flow, the browser is sent to `redirect_uri` with `code`; when the end-user cancels " +
            "it, with `error` set to `access_denied`. Either carries `state` when the authorization request did.",
        tags = ["oauth2"],
        parameters = [
            Parameter(
                name = "response_type",
                `in` = QUERY,
                description = "Kind of authorization response the client asks for, OAuth 2.1 §4.1.1. When absent, " +
                    "this server answers `unsupported_response_type`.",
                schema = Schema(
                    type = "string",
                    allowableValues = ["code"]
                )
            ),
            Parameter(
                name = "client_id",
                `in` = QUERY,
                description = "Identifier of the client making the authorization request. When absent, the " +
                    "authorization request fails.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "scope",
                `in` = QUERY,
                description = "Scopes the client asks for, separated by spaces. A scope that is not enabled, " +
                    "is a client scope only the `client_credentials` grant obtains, belongs to another " +
                    "audience or is not allowed to the client fails the authorization request. When absent, " +
                    "the `default-scopes` configured for the client.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "state",
                `in` = QUERY,
                description = "Opaque value this server returns unchanged as `state` when it sends the browser " +
                    "back to `redirect_uri`, OAuth 2.1 §4.1.1. When absent, the browser is sent back without " +
                    "one.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "nonce",
                `in` = QUERY,
                description = "Value this server copies unchanged into the `nonce` claim of the id token issued " +
                    "for the authorization code, OpenID Connect Core 1.0 §3.1.2.1. When absent, the id token " +
                    "carries no `nonce`.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "redirect_uri",
                `in` = QUERY,
                description = "URL the browser is sent back to once the flow ends: one of the " +
                    "`allowed-redirect-uris` configured for the client, matched exactly except for the port " +
                    "of a `127.0.0.1` or `[::1]` URL. When absent, the authorization request fails.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "code_challenge",
                `in` = QUERY,
                description = "PKCE code challenge, RFC 7636 §4.3. When absent, the authorization request fails.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "code_challenge_method",
                `in` = QUERY,
                description = "Method that derived `code_challenge` from the code verifier, RFC 7636 §4.3. " +
                    "When absent, `S256`.",
                schema = Schema(
                    type = "string",
                    allowableValues = ["S256"]
                )
            ),
            Parameter(
                name = "max_age",
                `in` = QUERY,
                description = "Maximum number of seconds since the end-user last authenticated, OpenID Connect " +
                    "Core 1.0 §3.1.2.1. The end-user signs in on every authorization request, so any value is " +
                    "met, and the id token states `auth_time`. A value that is not a non-negative integer fails " +
                    "the authorization request. When absent, no maximum.",
                schema = Schema(
                    type = "integer",
                    format = "int64"
                )
            ),
            Parameter(
                name = "invitation_token",
                `in` = QUERY,
                description = "Token of an invitation to bind to the interactive flow session, letting the " +
                    "end-user sign up where the audience of the client takes sign-ups by invitation only. An " +
                    "invitation that is unknown, consumed, revoked, expired or for another audience fails the " +
                    "authorization request. When absent, no invitation is bound.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "claims",
                `in` = QUERY,
                description = "Claims the client asks for by name in the id token or the userinfo response, as " +
                    "the JSON object OpenID Connect Core 1.0 §5.5 defines. A claim is added only where " +
                    "`claims.<id>.published-in-when-requested` names that response; any other name is ignored, " +
                    "and so are the `essential`, `value` and `values` members. A value that is not such a JSON " +
                    "object fails the authorization request. When absent, the scopes alone decide the claims.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "request",
                `in` = QUERY,
                description = "Request object carrying the authorization request as a JWT, OpenID Connect Core 1.0 " +
                    "§6.1. This server does not support request objects, and an authorization request carrying " +
                    "one fails.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "request_uri",
                `in` = QUERY,
                description = "URL of a request object carrying the authorization request, OpenID Connect Core " +
                    "1.0 §6.2. This server does not support request objects, and an authorization request " +
                    "carrying one fails.",
                schema = Schema(
                    type = "string"
                )
            )
        ],
        responses = [
            ApiResponse(
                responseCode = "303",
                description = "Sends the browser to the first step of the flow, with `state` identifying the " +
                    "interactive flow session. When the authorization request fails — `client_id` is missing " +
                    "or unknown, the client is not allowed the `authorization_code` grant, or a parameter is " +
                    "refused as its description says — the interactive flow session fails and the browser is " +
                    "sent to the error page of the flow instead, never to `redirect_uri`."
            ),
            ApiResponse(
                responseCode = "400",
                description = "- `unsupported_response_type`: `response_type` is missing or is not `code`."
            )
        ],
        externalDocs = ExternalDocumentation(
            description = "OAuth 2.1, §3.1",
            url = "https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-3.1"
        )
    )
    @Get
    suspend fun authorize(
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @QueryValue("response_type")
        responseType: String?,
        @QueryValue("client_id")
        uncheckedClientId: String?,
        @QueryValue("redirect_uri")
        uncheckedRedirectUri: String?,
        @QueryValue("scope")
        uncheckedScopes: String?,
        @QueryValue("state")
        uncheckedClientState: String?,
        @QueryValue("nonce")
        uncheckedClientNonce: String?,
        @QueryValue("code_challenge")
        uncheckedCodeChallenge: String?,
        @QueryValue("code_challenge_method")
        uncheckedCodeChallengeMethod: String?,
        @QueryValue("invitation_token")
        uncheckedInvitationToken: String?,
        @QueryValue("max_age")
        uncheckedMaxAge: String?,
        @QueryValue("claims")
        uncheckedClaims: String?,
        @QueryValue("request")
        uncheckedRequest: String? = null,
        @QueryValue("request_uri")
        uncheckedRequestUri: String? = null
    ): HttpResponse<*> {
        if (responseType.isNullOrBlank()) {
            throw oauth2ExceptionOf(UNSUPPORTED_RESPONSE_TYPE, "authorize.response_type.missing")
        }
        return when (enumValueOfWireNameOrNull<ResponseType>(responseType)) {
            ResponseType.CODE -> authorizeWithCodeFlow(
                observedRequest = observedRequest,
                authorizationRequest = UncheckedAuthorizationRequest(
                    clientId = uncheckedClientId,
                    redirectUri = uncheckedRedirectUri,
                    scopes = uncheckedScopes,
                    state = uncheckedClientState,
                    nonce = uncheckedClientNonce,
                    codeChallenge = uncheckedCodeChallenge,
                    codeChallengeMethod = uncheckedCodeChallengeMethod,
                    invitationToken = uncheckedInvitationToken,
                    maxAge = uncheckedMaxAge,
                    claims = uncheckedClaims,
                    requestObject = uncheckedRequest,
                    requestObjectUri = uncheckedRequestUri
                )
            )

            null -> throw oauth2ExceptionOf(
                UNSUPPORTED_RESPONSE_TYPE, "authorize.response_type.invalid",
                "responseType" to responseType
            )
        }
    }

    private suspend fun authorizeWithCodeFlow(
        observedRequest: ObservedRequest,
        authorizationRequest: UncheckedAuthorizationRequest
    ): HttpResponse<*> {
        val (session, flow) = interactiveAuthFlowSessionManager.startAuthorizationWith(authorizationRequest)
        interactiveAuthFlowSessionControllerUtil.observeStartedSession(session, observedRequest)

        val (steppedSession, step) = engine.advance(session)
        val redirectUri = stepUriMapper.toRedirectUri(
            session = steppedSession,
            flow = flow,
            step = step
        )
        return HttpResponse.seeOther<Any>(redirectUri)
    }

    companion object {
        const val OAUTH2_AUTHORIZE_ENDPOINT = "/api/oauth2/authorize"
    }
}
