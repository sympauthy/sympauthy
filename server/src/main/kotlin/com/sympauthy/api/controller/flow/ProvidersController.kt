package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.util.FLOW_FAILURE_SEE_OTHER_DESCRIPTION
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_SEE_OTHER_DESCRIPTION
import com.sympauthy.api.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2ProviderManager
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
        description = "Start signing in with a provider: redirect the browser to the provider's authorization " +
            "page, which sends it back to `/api/v1/flow/providers/{providerId}/callback`. An end-user " +
            "re-authenticating can only use a provider linked to their user. " + FLOW_FAILURE_SEE_OTHER_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "303",
                description = "Redirects the browser to the provider's authorization page or, where the " +
                    "interactive flow session does not continue there, to the step it continues at, to its error " +
                    "page, or, once it has ended, to the URL it returns the end-user to."
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.reauthentication.provider_not_linked`: the end-user is re-authenticating, and this " +
                    "provider is not linked to their user.\n" +
                    "- `flow.link_provider.wrong_provider`: the interactive flow session links a provider to the " +
                    "user, and `providerId` is not the one it was started for."
            ),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get(FLOW_PROVIDER_AUTHORIZE_ENDPOINT)
    suspend fun authorizeWithProvider(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Parameter(description = "Identifier of the provider.") providerId: String
    ): HttpResponse<*> =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
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

    /**
     * Anonymous rather than gated on the state, so that a browser returning from the provider without a
     * `state` is answered `auth.interactive_flow_session.validate.missing_state` instead of being refused
     * before the route is reached. The state is verified when the session is resolved.
     */
    @Operation(
        description = "Receive the browser back from a provider, and sign the end-user in, or up, with the " +
            "identity the provider vouches for. The provider redirects the browser here; a client never calls " +
            "this. An error the provider reports, or an answer without a code, fails the interactive flow " +
            "session. " + FLOW_FAILURE_SEE_OTHER_DESCRIPTION,
        responses = [
            ApiResponse(responseCode = "303", description = FLOW_SEE_OTHER_DESCRIPTION),
            ApiResponse(
                responseCode = "400",
                description = "- `auth.interactive_flow_session.validate.missing_state`: the request carries no " +
                    "`state`, or an empty one.\n" +
                    FLOW_STATE_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.reauthentication.provider_not_linked`: the end-user is re-authenticating, and the " +
                    "identity the provider vouches for is not linked to their user.\n" +
                    "- `user.create_with_provider.subject_taken`: the identity the provider vouches for was linked " +
                    "to another user meanwhile; signing in with the provider again reaches that user.\n" +
                    "- `user.claim_value_validator.invalid_type`: an identifier claim value the provider asserts " +
                    "for a new user is not of the claim's type.\n" +
                    "- `user.claim_value_validator.invalid_value`: such a value is not one the claim allows.\n" +
                    "- `user.claim_value_validator.invalid_number`: such a value of a `number` claim is not a " +
                    "whole number.\n" +
                    "- `user.claim_value_validator.invalid_email`: such a value of an `email` claim is not an " +
                    "email address.\n" +
                    "- `user.claim_value_validator.invalid_phone_number`: such a value of a `phone_number` claim " +
                    "is not a phone number."
            )
        ],
        tags = ["flow"]
    )
    @Get(FLOW_PROVIDER_CALLBACK_ENDPOINT)
    @Secured(IS_ANONYMOUS)
    suspend fun callback(
        @Parameter(description = "Identifier of the provider the browser returns from.") providerId: String,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @QueryValue("code") @Parameter(
            description = "Authorization code the provider issued. When absent, the interactive flow session fails."
        ) code: String?,
        @QueryValue("state") @Parameter(
            description = "`state` this server sent the browser to the provider with, which the provider sends " +
                "back. When absent, the request is refused with " +
                "`auth.interactive_flow_session.validate.missing_state`."
        ) state: String?,
        @QueryValue("error") @Parameter(
            description = "Error the provider answered the authorization with instead of a code, which fails the " +
                "interactive flow session. When absent, `code` is exchanged with the provider."
        ) error: String?,
        @QueryValue("error_description") @Parameter(
            description = "Description the provider gives of `error`, kept with the failure. When absent, none is " +
                "kept."
        ) errorDescription: String?
    ) = interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
        state = state,
        observedRequest = observedRequest,
        update = { session, _ ->
            interactiveFlowSessionOAuth2ProviderManager.signInOrSignUpUsingProvider(
                session = session,
                providerId = providerId,
                redirectUri = callbackUri(providerId),
                authorizeCode = code,
                observedRequest = observedRequest,
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
