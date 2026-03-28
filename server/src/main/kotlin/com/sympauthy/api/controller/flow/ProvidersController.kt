package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.business.manager.flow.WebAuthorizationFlowOAuth2ProviderManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller(FLOW_PROVIDER_ENDPOINTS)
class ProvidersController(
    @Inject private val webAuthorizationFlowOAuth2ProviderManager: WebAuthorizationFlowOAuth2ProviderManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil
) {

    @Operation(
        description = """
Redirect the end-user to the authorization flow of the provider identified by providerId.

If we cannot proceed with the redirection, instead the end-user will be redirect to the error page 
defined in ```urls.flow.error``` configuration.
        """,
        responses = [
            ApiResponse(
                responseCode = "303",
                description = "Redirect the end-user to continue the authorization flow."
            )
        ],
        tags = ["flow"]
    )
    @Get(FLOW_PROVIDER_AUTHORIZE_ENDPOINT)
    suspend fun authorizeWithProvider(
        authentication: Authentication,
        providerId: String
    ): HttpResponse<*> =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenRunAndRedirect(
            state = authentication.stateOrNull,
            run = { authorizeAttempt, _ ->
                webAuthorizationFlowOAuth2ProviderManager.authorizeWithProvider(
                    authorizeAttempt,
                    providerId = providerId
                )
            },
            mapRedirectUriToResource = { redirectUri -> HttpResponse.seeOther<Any>(redirectUri) },
            mapResultToResource = { HttpResponse.seeOther<Any>(it) }
        )

    @Operation(
        description = """
Callback that providers should redirect at the end of their OAuth2 Authorization code flow. It will redirect the end-user to:
 - an authorization flow if we need more information from the end-user to complete the authorization process.
 - the client if the authentication flow is completed.
        """,
        responses = [
            ApiResponse(
                responseCode = "303",
                description = """
Redirection to either:
- an authorization flow if we need more information from the end-user to complete the authorization process.
- the client if the authentication flow is completed.
                """
            )
        ],
        tags = ["flow"]
    )
    @Get(FLOW_PROVIDER_CALLBACK_ENDPOINT)
    @Secured(IS_ANONYMOUS)
    suspend fun callback(
        providerId: String,
        @QueryValue("code") code: String?,
        @QueryValue("state") state: String?
    ) = webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenUpdateAndRedirect(
        state = state,
        update = { authorizeAttempt, _ ->
            val (updatedAuthorizeAttempt, _) = webAuthorizationFlowOAuth2ProviderManager.signInOrSignUpUsingProvider(
                authorizeAttempt = authorizeAttempt,
                providerId = providerId,
                authorizeCode = code
            )
            updatedAuthorizeAttempt
        },
        mapRedirectUriToResource = { redirectUri -> HttpResponse.seeOther<Any>(redirectUri) }
    )

    companion object {
        const val FLOW_PROVIDER_ENDPOINTS = "/api/v1/flow/providers/{providerId}"
        const val FLOW_PROVIDER_AUTHORIZE_ENDPOINT = "/authorize"
        const val FLOW_PROVIDER_CALLBACK_ENDPOINT = "/callback"
    }
}
