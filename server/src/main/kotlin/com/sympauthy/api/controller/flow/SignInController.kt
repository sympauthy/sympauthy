package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_AUTHORIZE_ENDPOINT
import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.resource.flow.PasswordResource
import com.sympauthy.api.resource.flow.ProviderResource
import com.sympauthy.api.resource.flow.SignInFlowResource
import com.sympauthy.api.resource.flow.SignInInputResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowPasswordManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Controller("/api/v1/flow/sign-in")
@Secured(HAS_STATE)
class SignInController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val passwordFlowManager: WebAuthorizationFlowPasswordManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Operation(
        description = """
Return everything the custom UI needs to render the sign-in step, or a redirect URL if the end-user is not
expected to be on the sign-in step (ex. an invitation flow redirects to sign-up, an authenticated end-user
is redirected to the next step).

This configuration only contains information associated to this authorization server, the client and the
on-going flow. All URLs it contains already include the state query param.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getSignInConfiguration(
        authentication: Authentication
    ): SignInFlowResource = webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenRunAndRedirect(
        state = authentication.stateOrNull,
        run = { authorizeAttempt, flow ->
            if (signInApplies(authorizeAttempt, flow)) {
                buildSignInConfiguration(authorizeAttempt, flow)
            } else {
                null
            }
        },
        mapResultToResource = { it },
        mapRedirectUriToResource = { SignInFlowResource(redirectUrl = it.toString()) }
    )

    /**
     * The sign-in step applies while no user is associated to the [authorizeAttempt] yet, unless the flow is an
     * invitation flow with a sign-up page (in which case the end-user must be redirected to sign-up).
     * The predicate mirrors [WebAuthorizationFlowRedirectUriBuilder] so a not-applicable step never redirects to itself.
     */
    private fun signInApplies(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        flow: WebAuthorizationFlow
    ): Boolean {
        if (authorizeAttempt.userId != null) {
            return false
        }
        return authorizeAttempt.invitationId == null || flow.signUpUri == null
    }

    private suspend fun buildSignInConfiguration(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        flow: WebAuthorizationFlow
    ): SignInFlowResource {
        val urlsConfig = uncheckedUrlsConfig.orThrow()
        val password = if (passwordFlowManager.signInEnabled) {
            PasswordResource(
                identifierClaims = claimManager.listIdentifierClaims().map { it.id }
            )
        } else null
        val providers = providerManager.listEnabledProviders()
            .takeIf(List<EnabledProvider>::isNotEmpty)
            ?.map { getProvider(it, authorizeAttempt, urlsConfig) }
        val signUpRedirectUrl = if (
            webAuthorizationFlowManager.isSignUpAllowed(authorizeAttempt) && flow.signUpUri != null
        ) {
            redirectUriBuilder.getSignUpRedirectUri(authorizeAttempt, flow)?.toString()
        } else null
        return SignInFlowResource(
            password = password,
            providers = providers,
            signUpRedirectUrl = signUpRedirectUrl
        )
    }

    private suspend fun getProvider(
        provider: EnabledProvider,
        authorizeAttempt: AuthorizeAttempt,
        urlsConfig: EnabledUrlsConfig
    ): ProviderResource {
        val authorizeUri = urlsConfig.getUri(
            FLOW_PROVIDER_ENDPOINTS + FLOW_PROVIDER_AUTHORIZE_ENDPOINT,
            "providerId" to provider.id
        )
        val authorizeUrl = redirectUriBuilder.appendStateToUri(authorizeAttempt, authorizeUri)
        return ProviderResource(
            id = provider.id,
            name = provider.name,
            authorizeUrl = authorizeUrl.toString()
        )
    }

    @Operation(
        description = "Sign-in using a login and a password.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The credentials provided where valid. The authentication flow will continue.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun signIn(
        authentication: Authentication,
        @Body inputResource: SignInInputResource
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { authorizeAttempt, _ ->
                passwordFlowManager.signInWithPassword(
                    authorizeAttempt = authorizeAttempt,
                    login = inputResource.login,
                    password = inputResource.password
                )
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
