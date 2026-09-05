package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_AUTHORIZE_ENDPOINT
import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.mapper.flow.CollectableClaimResourceMapper
import com.sympauthy.api.resource.flow.PasswordResource
import com.sympauthy.api.resource.flow.ProviderResource
import com.sympauthy.api.resource.flow.SignInFlowResource
import com.sympauthy.api.resource.flow.SignInInputResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionPasswordManager
import com.sympauthy.business.manager.password.PasswordManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import java.util.Locale

@Controller("/api/v1/flow/sign-in")
@Secured(HAS_STATE)
class SignInController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val passwordFlowManager: InteractiveAuthFlowSessionPasswordManager,
    @Inject private val passwordManager: PasswordManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val collectableClaimResourceMapper: CollectableClaimResourceMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
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
        authentication: Authentication,
        httpRequest: HttpRequest<*>
    ): SignInFlowResource {
        val locale = httpRequest.locale.orDefault()
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
            run = { session, flow ->
                if (signInApplies(session, flow)) {
                    buildSignInConfiguration(session, flow, locale)
                } else {
                    null
                }
            },
            mapResultToResource = { it },
            mapRedirectUriToResource = { SignInFlowResource(redirectUrl = it.toString()) }
        )
    }

    /**
     * The sign-in step applies while no user is associated to the [session] yet, unless the flow is an
     * invitation flow with a sign-up page (in which case the end-user must be redirected to sign-up).
     * The predicate mirrors [OAuth2AuthorizeInteractiveFlowPurposeHandler] so a not-applicable step never redirects to
     * itself.
     *
     * A [session] whose user is already fixed normally does not apply — **except** under a
     * [InteractiveFlowPurpose.REAUTHENTICATION] gate, which reuses the sign-in step to make that user re-prove
     * ownership. The re-authentication check runs only when a user is already set, so the common
     * unauthenticated sign-in path is unaffected.
     */
    private suspend fun signInApplies(
        session: OnGoingInteractiveFlowSession,
        flow: InteractiveFlow
    ): Boolean {
        if (session.userId != null) {
            return engine.currentPurposeOrNull(session) == InteractiveFlowPurpose.REAUTHENTICATION
        }
        val invitationId = oauth2Manager.fetchOAuth2(session).invitationId
        return invitationId == null || flow.signUpUri == null
    }

    private suspend fun buildSignInConfiguration(
        session: OnGoingInteractiveFlowSession,
        flow: InteractiveFlow,
        locale: Locale
    ): SignInFlowResource {
        val urlsConfig = uncheckedUrlsConfig.orThrow()
        // When re-authenticating, the session user is already fixed (see signInApplies) and only the methods
        // that account actually has are offered — password if it has one, providers already linked to it —
        // and there is no sign-up. Otherwise this is a normal sign-in and every configured method is offered.
        val reauthUserId = session.userId

        val password = if (
            passwordFlowManager.signInEnabled &&
            (reauthUserId == null || passwordManager.hasPassword(reauthUserId))
        ) {
            PasswordResource(
                identifierClaims = collectableClaimResourceMapper.toResources(
                    claimManager.listIdentifierClaims(), locale
                )
            )
        } else null

        val enabledProviders = providerManager.listEnabledProviders()
        val offeredProviders = if (reauthUserId != null) {
            val linkedProviderIds = providerClaimsManager.findByUserId(reauthUserId)
                .map { it.providerId }
                .toSet()
            enabledProviders.filter { it.id in linkedProviderIds }
        } else {
            enabledProviders
        }
        val providers = offeredProviders
            .takeIf(List<EnabledProvider>::isNotEmpty)
            ?.map { getProvider(it, session, urlsConfig) }

        val signUpRedirectUrl = if (
            reauthUserId == null &&
            interactiveAuthFlowSessionManager.isSignUpAllowed(session) && flow.signUpUri != null
        ) {
            stepUriMapper.getSignUpRedirectUri(session, flow)?.toString()
        } else null

        // A re-authenticating account with no offerable method (no password set, its providers disabled or
        // unlinked) would otherwise be served an empty sign-in page with no way to prove ownership — fail the
        // flow with a clear error instead of stranding the end-user on a dead-end step.
        if (reauthUserId != null && password == null && providers == null) {
            throw businessExceptionOf(
                "flow.reauthentication.no_method",
                "description.flow.reauthentication.no_method"
            )
        }

        return SignInFlowResource(
            password = password,
            providers = providers,
            signUpRedirectUrl = signUpRedirectUrl
        )
    }

    private suspend fun getProvider(
        provider: EnabledProvider,
        session: InteractiveFlowSession,
        urlsConfig: EnabledUrlsConfig
    ): ProviderResource {
        val authorizeUri = urlsConfig.getUri(
            FLOW_PROVIDER_ENDPOINTS + FLOW_PROVIDER_AUTHORIZE_ENDPOINT,
            "providerId" to provider.id
        )
        val authorizeUrl = stepUriMapper.appendState(session, authorizeUri)
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
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { session, _ ->
                passwordFlowManager.signInWithPassword(
                    session = session,
                    login = inputResource.login,
                    password = inputResource.password
                )
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
