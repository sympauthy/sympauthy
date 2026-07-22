package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_AUTHORIZE_ENDPOINT
import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.resource.flow.PasswordConfigurationResource
import com.sympauthy.api.resource.flow.ProviderConfigurationResource
import com.sympauthy.api.resource.flow.SignInConfigurationResource
import com.sympauthy.api.resource.flow.SignInInputResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowPasswordManager
import com.sympauthy.business.manager.provider.ProviderManager
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Controller("/api/v1/flow/sign-in")
@Secured(HAS_STATE)
class SignInController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val passwordFlowManager: WebAuthorizationFlowPasswordManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Operation(
        description = """
Return the sign-in capabilities of this authorization server: the authentication methods the
end-user can use to sign in, such as password-based authentication and the third-party providers
that can be used to authenticate the end-user.

This configuration only contains information that is associated to this authorization server and the
client, they can be cached across the different end-users trying to authenticate.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getSignInConfiguration(
        authentication: Authentication
    ): SignInConfigurationResource = webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenRun(
        state = authentication.stateOrNull
    ) { _, _ ->
        coroutineScope {
            val urlsConfig = uncheckedUrlsConfig.orThrow()
            val deferredProviders = async {
                providerManager.listEnabledProviders()
                    .takeIf(List<EnabledProvider>::isNotEmpty)
                    ?.map { getProvider(it, urlsConfig) }
            }
            SignInConfigurationResource(
                passwordSignIn = passwordFlowManager.signInEnabled,
                password = getPassword(),
                providers = deferredProviders.await()
            )
        }
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

    private fun getPassword(): PasswordConfigurationResource? {
        if (!passwordFlowManager.signInEnabled) {
            return null
        }
        return PasswordConfigurationResource(
            identifierClaims = claimManager.listIdentifierClaims().map { it.id }
        )
    }

    private fun getProvider(
        provider: EnabledProvider,
        urlsConfig: EnabledUrlsConfig
    ): ProviderConfigurationResource {
        val authorizeUrl = urlsConfig.getUri(
            FLOW_PROVIDER_ENDPOINTS + FLOW_PROVIDER_AUTHORIZE_ENDPOINT,
            "providerId" to provider.id
        )
        return ProviderConfigurationResource(
            id = provider.id,
            name = provider.name,
            authorizeUrl = authorizeUrl.toString()
        )
    }
}
