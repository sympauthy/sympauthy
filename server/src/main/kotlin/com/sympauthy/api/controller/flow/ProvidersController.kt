package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2ProviderManager
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
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
import java.net.URI

@Secured(HAS_STATE)
@Controller(FLOW_PROVIDER_ENDPOINTS)
class ProvidersController(
    @Inject private val interactiveFlowSessionOAuth2ProviderManager: InteractiveFlowSessionOAuth2ProviderManager,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    /**
     * Return the absolute uri the provider identified by [providerId] must redirect the end-user back to at
     * the end of its authorization code flow.
     *
     * Both handlers below build it here rather than each spelling the route: RFC 6749 section 4.1.3 requires
     * the token request to repeat the redirect uri of the authorization request, so the two have to agree.
     */
    private fun callbackUri(providerId: String): URI = uncheckedUrlsConfig.orThrow().getUri(
        FLOW_PROVIDER_ENDPOINTS + FLOW_PROVIDER_CALLBACK_ENDPOINT,
        "providerId" to providerId
    )

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
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
            run = { session, _ ->
                interactiveFlowSessionOAuth2ProviderManager.authorizeWithProvider(
                    session,
                    providerId = providerId,
                    redirectUri = callbackUri(providerId)
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
        @QueryValue("state") state: String?,
        @QueryValue("error") error: String?,
        @QueryValue("error_description") errorDescription: String?
    ) = interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
        state = state,
        update = { session, _ ->
            interactiveFlowSessionOAuth2ProviderManager.signInOrSignUpUsingProvider(
                session = session,
                providerId = providerId,
                redirectUri = callbackUri(providerId),
                authorizeCode = code,
                providerError = error,
                providerErrorDescription = errorDescription
            )
        },
        mapRedirectUriToResource = { redirectUri -> HttpResponse.seeOther<Any>(redirectUri) }
    )

    companion object {
        const val FLOW_PROVIDER_ENDPOINTS = "/api/v1/flow/providers/{providerId}"
        const val FLOW_PROVIDER_AUTHORIZE_ENDPOINT = "/authorize"
        const val FLOW_PROVIDER_CALLBACK_ENDPOINT = "/callback"
    }
}
