package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.controller.oauth2.AuthorizeController.Companion.OAUTH2_AUTHORIZE_ENDPOINT
import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_RESPONSE_TYPE
import com.sympauthy.business.model.oauth2.ResponseType
import com.sympauthy.business.model.security.ObservedRequest
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
import jakarta.inject.Inject

@Controller(OAUTH2_AUTHORIZE_ENDPOINT)
@Secured(IS_ANONYMOUS)
@Suppress("MaxLineLength")
open class AuthorizeController(
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = """
The authorization endpoint is used to interact with the resource owner and obtain an authorization grant.
""",
        tags = ["oauth2"],
        parameters = [
            Parameter(
                name = "response_type",
                `in` = QUERY,
                description = "",
                schema = Schema(
                    type = "string",
                    allowableValues = ["code"]
                )
            ),
            Parameter(
                name = "client_id",
                `in` = QUERY,
                description = "The identifier of the client that initiated the authentication grant.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "scope",
                `in` = QUERY,
                description = "The scope of the access request.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "state",
                `in` = QUERY,
                description = """
An opaque value used by the client to maintain state between the request and callback. 
The authorization server includes this value when redirecting the user-agent back to the client.
                """,
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "nonce",
                `in` = QUERY,
                description = """
An opaque value used to associate a Client session with an ID Token, and to mitigate replay attacks.
The authorization server includes this value unmodified in the ID Token.
                """,
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "redirect_uri",
                `in` = QUERY,
                description =
                    "The url where the end-user must be redirected at the end of the authorization code grant flow.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "code_challenge",
                `in` = QUERY,
                description = "PKCE code challenge (RFC 7636). Required for all clients per OAuth 2.1.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "code_challenge_method",
                `in` = QUERY,
                description = "PKCE code challenge method (RFC 7636). Only 'S256' is supported. Defaults to 'S256' if code_challenge is present.",
                schema = Schema(
                    type = "string",
                    allowableValues = ["S256"]
                )
            ),
            Parameter(
                name = "max_age",
                `in` = QUERY,
                description = "Maximum number of seconds since the end-user last authenticated, per OpenID Connect Core 3.1.2.1. This server keeps no session between authorizations, so every authorization signs the end-user in anew and any non-negative value is satisfied by construction; the id token it issues states `auth_time`. A value that is not a non-negative integer is refused.",
                schema = Schema(
                    type = "integer",
                    format = "int64"
                )
            ),
            Parameter(
                name = "invitation_token",
                `in` = QUERY,
                description = "Invitation token to bind to this authorization flow. When provided, the invitation is validated and bound to the flow state.",
                schema = Schema(
                    type = "string"
                )
            ),
            Parameter(
                name = "claims",
                `in` = QUERY,
                description = "The claims this client asks to have delivered in the id token or in the /userinfo response, as the JSON object OpenID Connect Core 5.5 defines: an `id_token` and a `userinfo` member, each naming claims. It only ever adds to what the requested scopes already deliver, and it adds a claim to a channel only where the deployment opened that channel to a request — a claim asked for in a channel the deployment did not open is absent, with no error. The `essential`, `value` and `values` members of 5.5.1 are read past and ignored, which 5.5.1 permits. A value that is not such a JSON object is refused.",
                schema = Schema(
                    type = "string"
                )
            )
        ],
        externalDocs = ExternalDocumentation(
            description = "Authorize Endpoint specification",
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
        uncheckedClaims: String?
    ): HttpResponse<*> {
        if (responseType.isNullOrBlank()) {
            throw oauth2ExceptionOf(UNSUPPORTED_RESPONSE_TYPE, "authorize.response_type.missing")
        }
        return when (ResponseType.fromWireNameOrNull(responseType)) {
            ResponseType.CODE -> authorizeWithCodeFlow(
                observedRequest = observedRequest,
                uncheckedClientId = uncheckedClientId,
                uncheckedClientState = uncheckedClientState,
                uncheckedClientNonce = uncheckedClientNonce,
                uncheckedScopes = uncheckedScopes,
                uncheckedRedirectUri = uncheckedRedirectUri,
                uncheckedCodeChallenge = uncheckedCodeChallenge,
                uncheckedCodeChallengeMethod = uncheckedCodeChallengeMethod,
                uncheckedInvitationToken = uncheckedInvitationToken,
                uncheckedMaxAge = uncheckedMaxAge,
                uncheckedClaims = uncheckedClaims
            )

            null -> throw oauth2ExceptionOf(
                UNSUPPORTED_RESPONSE_TYPE, "authorize.response_type.invalid",
                "responseType" to responseType
            )
        }
    }

    private suspend fun authorizeWithCodeFlow(
        observedRequest: ObservedRequest,
        uncheckedClientId: String?,
        uncheckedClientState: String?,
        uncheckedClientNonce: String?,
        uncheckedScopes: String?,
        uncheckedRedirectUri: String?,
        uncheckedCodeChallenge: String?,
        uncheckedCodeChallengeMethod: String?,
        uncheckedInvitationToken: String?,
        uncheckedMaxAge: String?,
        uncheckedClaims: String?
    ): HttpResponse<*> {
        val (session, flow) = interactiveAuthFlowSessionManager.startAuthorizationWith(
            uncheckedClientId = uncheckedClientId,
            uncheckedClientState = uncheckedClientState,
            uncheckedClientNonce = uncheckedClientNonce,
            uncheckedScopes = uncheckedScopes,
            uncheckedRedirectUri = uncheckedRedirectUri,
            uncheckedCodeChallenge = uncheckedCodeChallenge,
            uncheckedCodeChallengeMethod = uncheckedCodeChallengeMethod,
            uncheckedInvitationToken = uncheckedInvitationToken,
            uncheckedMaxAge = uncheckedMaxAge,
            uncheckedClaims = uncheckedClaims
        )
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
