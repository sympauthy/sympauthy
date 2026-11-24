package com.sympauthy.api.flow

import com.sympauthy.api.FLOW_SURFACE
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.flow.ProvidersController.Companion.FLOW_PROVIDER_AUTHORIZE_ENDPOINT
import com.sympauthy.api.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.api.flow.mapper.ClientReferenceResourceMapper
import com.sympauthy.api.flow.mapper.CollectableClaimResourceMapper
import com.sympauthy.api.flow.resource.PasswordResource
import com.sympauthy.api.flow.resource.ProviderResource
import com.sympauthy.api.flow.resource.SignInFlowResource
import com.sympauthy.api.flow.resource.SignInInputResource
import com.sympauthy.api.flow.resource.SimpleFlowResource
import com.sympauthy.api.flow.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.mapper.InteractiveFlowStepUriMapper
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.claim.manager.ClaimManager
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.flow.auth.manager.InteractiveAuthFlowSessionManager
import com.sympauthy.business.flow.auth.manager.InteractiveAuthFlowSessionPasswordManager
import com.sympauthy.business.flow.auth.manager.InteractiveAuthFlowSessionPasswordResetManager
import com.sympauthy.business.flow.manager.InteractiveFlowEngine
import com.sympauthy.business.flow.manager.InteractiveFlowSessionManager
import com.sympauthy.business.flow.model.InteractiveFlow
import com.sympauthy.business.flow.model.InteractiveFlowPurpose
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.flow.oauth2.manager.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.password.manager.PasswordManager
import com.sympauthy.business.provider.manager.ProviderClaimsManager
import com.sympauthy.business.provider.manager.ProviderManager
import com.sympauthy.business.provider.model.EnabledProvider
import com.sympauthy.business.securitycontext.model.ObservedRequest
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import com.sympauthy.platform.util.orDefault
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import java.util.Locale

@Controller("$FLOW_SURFACE/sign-in")
@Secured(HAS_STATE)
class SignInController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val passwordFlowManager: InteractiveAuthFlowSessionPasswordManager,
    @Inject private val passwordResetManager: InteractiveAuthFlowSessionPasswordResetManager,
    @Inject private val passwordManager: PasswordManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val collectableClaimResourceMapper: CollectableClaimResourceMapper,
    @Inject private val clientMapper: ClientReferenceResourceMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Operation(
        description = "Retrieve the sign-in step: the methods the end-user can sign in with. An end-user " +
            "re-authenticating is offered only the methods their user holds. When the end-user is not expected " +
            "to sign in, for example once signed in or when an invitation sends them to sign up, the response " +
            "carries only `redirect_url`. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The configuration of the sign-in step, or the `redirect_url` to send the browser " +
                    "to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getSignInConfiguration(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        httpRequest: HttpRequest<*>
    ): SignInFlowResource {
        val locale = httpRequest.locale.orDefault()
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, flow ->
                if (signInApplies(session, flow)) {
                    buildSignInConfiguration(session, flow, locale, authentication.stateOrNull)
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
        locale: Locale,
        state: String?
    ): SignInFlowResource {
        val urlsConfig = uncheckedUrlsConfig.orThrow()
        // When re-authenticating, the session user is already fixed (see signInApplies) and only the methods
        // that account actually has are offered — password if it has one, providers already linked to it —
        // and there is no sign-up. Otherwise this is a normal sign-in and every configured method is offered.
        val reauthUser = session.userId?.let { sessionManager.getUser(session) }

        val password = if (
            passwordFlowManager.signInEnabled &&
            (reauthUser == null || passwordManager.hasPassword(reauthUser))
        ) {
            PasswordResource(
                identifierClaims = collectableClaimResourceMapper.toResources(
                    claimManager.listIdentifierClaims(), locale
                )
            )
        } else null

        val enabledProviders = providerManager.listEnabledProviders()
        val offeredProviders = if (reauthUser != null) {
            val linkedProviderIds = providerClaimsManager.findByUserId(reauthUser.id)
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
            reauthUser == null &&
            interactiveAuthFlowSessionManager.isSignUpAllowed(session) && flow.signUpUri != null
        ) {
            stepUriMapper.getSignUpRedirectUri(session, flow)?.toString()
        } else null

        val resetPasswordRedirectUrl = if (reauthUser == null && passwordResetManager.isOffered(flow)) {
            stepUriMapper.getResetPasswordRedirectUriOrNull(session, flow)?.toString()
        } else null

        // A re-authenticating account with no offerable method (no password set, its providers disabled or
        // unlinked) would otherwise be served an empty sign-in page with no way to prove ownership — fail the
        // flow with a clear error instead of stranding the end-user on a dead-end step.
        if (reauthUser != null && password == null && providers == null) {
            throw businessExceptionOf(
                "flow.reauthentication.no_method",
                "description.flow.reauthentication.no_method"
            )
        }

        return SignInFlowResource(
            password = password,
            providers = providers,
            signUpRedirectUrl = signUpRedirectUrl,
            resetPasswordRedirectUrl = resetPasswordRedirectUrl,
            client = clientMapper.toResourceOrNull(session),
            cancelUrl = stepUriMapper.getCancelUriOrNull(session, state)?.toString()
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
        description = "Sign the end-user in with a login and a password. An end-user re-authenticating must " +
            "sign in as their own user. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = FLOW_REDIRECT_RESPONSE_DESCRIPTION,
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.password.sign_in.invalid`: `login` or `password` is absent, or the two match no " +
                    "enabled user.\n" +
                    "- `flow.reauthentication.wrong_account`: the end-user is re-authenticating, and `login` and " +
                    "`password` are another user's.\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun signIn(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: SignInInputResource
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, _ ->
                passwordFlowManager.signInWithPassword(
                    session = session,
                    login = inputResource.login,
                    password = inputResource.password,
                    observedRequest = observedRequest
                )
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
