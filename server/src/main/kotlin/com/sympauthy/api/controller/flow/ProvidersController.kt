package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowOauth2ProviderManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.provider.ProviderConfigManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.security.SecurityRule.HAS_STATE
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
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val oauth2ProviderManager: WebAuthorizationFlowOauth2ProviderManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val providerConfigManager: ProviderConfigManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder,

    ) {

    @Operation(
        description = """
Redirect the end-user to the authorization flow of the provider identified by providerId.

If we cannot proceed with the redirection, instead the end-user will be redirect to the error page defined in ```urls.flow.error``` configuration.
Following query parameters will be populated with information about the error:
- ```error_code```: The technical identifier of this error.
- ```details```: A message containing technical details about the error.
- ```description```: (optional) A message explaining the error to the end-user. It may contain information on how to recover from the issue.
        """,
        responses = [
            ApiResponse(
                responseCode = "303",
                description = ""
            )
        ],
        tags = ["flow"]
    )
    @Get(FLOW_PROVIDER_AUTHORIZE_ENDPOINT)
    suspend fun authorizeWithProvider(
        authentication: Authentication,
        providerId: String
    ): HttpResponse<*> =
        webAuthorizationFlowManager.extractFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, _ ->
            val provider = providerConfigManager.findEnabledProviderById(providerId)
            providerManager.authorizeWithProvider(authorizeAttempt, provider)
        }

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
    ) = webAuthorizationFlowManager.extractOnGoingFromStateAndRun<HttpResponse<Any>>(state) { authorizeAttempt, flow ->
        val provider = providerConfigManager.findEnabledProviderById(providerId)
        val result = oauth2ProviderManager.signInOrSignUpUsingProvider(
            authorizeAttempt = authorizeAttempt,
            provider = provider,
            authorizeCode = code
        )
        val url = redirectUriBuilder.getRedirectUri(
            authorizeAttempt = authorizeAttempt,
            flow = flow,
            status = result
        )
        HttpResponse.temporaryRedirect<Any>(url)
    }

    companion object {
        const val FLOW_PROVIDER_ENDPOINTS = "/api/v1/flow/providers/{providerId}"
        const val FLOW_PROVIDER_AUTHORIZE_ENDPOINT = "/authorize"
        const val FLOW_PROVIDER_CALLBACK_ENDPOINT = "/callback"
    }
}
