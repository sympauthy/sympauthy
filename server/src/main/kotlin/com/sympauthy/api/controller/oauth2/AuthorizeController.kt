package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.AuthorizeController.Companion.OAUTH2_AUTHORIZE_ENDPOINT
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_RESPONSE_TYPE
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
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
open class AuthorizeController(
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val webFlowRedirectBuilder: WebAuthorizationFlowRedirectUriBuilder
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
                description = "The url where the end-user must be redirected at the end of the authorization code grant flow.",
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
            )
        ],
        externalDocs = ExternalDocumentation(
            description = "Authorize Endpoint specification",
            url = "https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1#section-3.1"
        )
    )
    @Get
    suspend fun authorize(
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
        uncheckedCodeChallengeMethod: String?
    ): HttpResponse<*> {
        return when {
            responseType.isNullOrBlank() -> throw oauth2ExceptionOf(
                UNSUPPORTED_RESPONSE_TYPE, "authorize.response_type.missing"
            )

            responseType == "code" -> authorizeWithCodeFlow(
                uncheckedClientId = uncheckedClientId,
                uncheckedClientState = uncheckedClientState,
                uncheckedClientNonce = uncheckedClientNonce,
                uncheckedScopes = uncheckedScopes,
                uncheckedRedirectUri = uncheckedRedirectUri,
                uncheckedCodeChallenge = uncheckedCodeChallenge,
                uncheckedCodeChallengeMethod = uncheckedCodeChallengeMethod
            )

            else -> throw oauth2ExceptionOf(
                UNSUPPORTED_RESPONSE_TYPE, "authorize.response_type.invalid",
                "responseType" to responseType
            )
        }
    }

    private suspend fun authorizeWithCodeFlow(
        uncheckedClientId: String?,
        uncheckedClientState: String?,
        uncheckedClientNonce: String?,
        uncheckedScopes: String?,
        uncheckedRedirectUri: String?,
        uncheckedCodeChallenge: String?,
        uncheckedCodeChallengeMethod: String?
    ): HttpResponse<*> {
        val (authorizeAttempt, flow) = webAuthorizationFlowManager.startAuthorizationWith(
            uncheckedClientId = uncheckedClientId,
            uncheckedClientState = uncheckedClientState,
            uncheckedClientNonce = uncheckedClientNonce,
            uncheckedScopes = uncheckedScopes,
            uncheckedRedirectUri = uncheckedRedirectUri,
            uncheckedCodeChallenge = uncheckedCodeChallenge,
            uncheckedCodeChallengeMethod = uncheckedCodeChallengeMethod
        )
        val status = webAuthorizationFlowManager.getStatus(authorizeAttempt)
        val redirectUri = webFlowRedirectBuilder.getRedirectUri(
            authorizeAttempt = authorizeAttempt,
            flow = flow,
            status = status
        )
        return HttpResponse.temporaryRedirect<Any>(redirectUri)
    }

    companion object {
        const val OAUTH2_AUTHORIZE_ENDPOINT = "/api/oauth2/authorize"
    }
}
