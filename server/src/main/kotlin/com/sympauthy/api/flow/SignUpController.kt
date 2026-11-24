package com.sympauthy.api.flow

import com.sympauthy.api.FLOW_SURFACE
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.flow.mapper.ClientReferenceResourceMapper
import com.sympauthy.api.flow.mapper.CollectableClaimResourceMapper
import com.sympauthy.api.flow.resource.PasswordResource
import com.sympauthy.api.flow.resource.SignUpFlowResource
import com.sympauthy.api.flow.resource.SignUpInputResource
import com.sympauthy.api.flow.resource.SimpleFlowResource
import com.sympauthy.api.flow.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.InteractiveFlowStepUriMapper
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.claim.manager.ClaimManager
import com.sympauthy.business.flow.auth.manager.InteractiveAuthFlowSessionManager
import com.sympauthy.business.flow.auth.manager.InteractiveAuthFlowSessionPasswordManager
import com.sympauthy.business.flow.model.InteractiveFlow
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.flow.oauth2.manager.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.securitycontext.model.ObservedRequest
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

@Secured(HAS_STATE)
@Controller("$FLOW_SURFACE/sign-up")
class SignUpController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val passwordFlowManager: InteractiveAuthFlowSessionPasswordManager,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val collectableClaimResourceMapper: CollectableClaimResourceMapper,
    @Inject private val clientMapper: ClientReferenceResourceMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = "Retrieve the sign-up step: how the end-user can create a user. When the end-user is not " +
            "expected to sign up, for example once signed in or when the client's audience does not allow " +
            "signing up, the response carries only `redirect_url`. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The configuration of the sign-up step, or the `redirect_url` to send the browser " +
                    "to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getSignUpConfiguration(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        httpRequest: HttpRequest<*>
    ): SignUpFlowResource {
        val locale = httpRequest.locale.orDefault()
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, flow ->
                if (signUpApplies(session)) {
                    buildSignUpConfiguration(session, flow, locale, authentication.stateOrNull)
                } else {
                    null
                }
            },
            mapResultToResource = { it },
            mapRedirectUriToResource = { SignUpFlowResource(redirectUrl = it.toString()) }
        )
    }

    /**
     * The sign-up step applies while no user is associated to the [session] yet and sign-up is allowed for
     * the audience of the client. When it does not apply, [OAuth2AuthorizeInteractiveFlowPurposeHandler] redirects an
     * unauthenticated end-user with no invitation to the sign-in page.
     */
    private suspend fun signUpApplies(
        session: OnGoingInteractiveFlowSession
    ): Boolean {
        if (session.userId != null) {
            return false
        }
        return interactiveAuthFlowSessionManager.isSignUpAllowed(session)
    }

    private suspend fun buildSignUpConfiguration(
        session: OnGoingInteractiveFlowSession,
        flow: InteractiveFlow,
        locale: Locale,
        state: String?
    ): SignUpFlowResource {
        val password = if (passwordFlowManager.signUpEnabled) {
            PasswordResource(
                identifierClaims = collectableClaimResourceMapper.toResources(
                    claimManager.listIdentifierClaims(), locale
                )
            )
        } else null
        val signInRedirectUrl = if (oauth2Manager.fetchOAuth2(session).invitationId == null) {
            stepUriMapper.getSignInRedirectUri(session, flow).toString()
        } else null
        return SignUpFlowResource(
            password = password,
            signInRedirectUrl = signInRedirectUrl,
            client = clientMapper.toResourceOrNull(session),
            cancelUrl = stepUriMapper.getCancelUriOrNull(session, state)?.toString()
        )
    }

    @Operation(
        description = "Create a user with the password and the identifier claims the end-user entered, and " +
            "sign the end-user in as that user. Any other claim in the request is not saved. Once the " +
            "end-user is signed in, a repeated sign-up creates nothing. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = FLOW_REDIRECT_RESPONSE_DESCRIPTION,
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.claims.invalid`: one or more claim values are refused, and `properties` holds the " +
                    "failure of each by claim identifier.\n" +
                    "- `flow.sign_up.disabled`: the client's audience allows neither signing up nor invitations.\n" +
                    "- `flow.sign_up.invitation_required`: the client's audience allows signing up only with an " +
                    "invitation, and the flow was started without one.\n" +
                    "- `flow.password.sign_up.missing_claim`: an identifier claim has no value.\n" +
                    "- `flow.password.sign_up.existing`: another user already holds one of the identifier claim " +
                    "values.\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun signUp(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: SignUpInputResource
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, _ ->
                val updates = collectedClaimUpdateMapper.toUpdates(inputResource.claims)
                passwordFlowManager.signUpWithClaimsAndPassword(
                    session = session,
                    unfilteredUpdates = updates,
                    password = inputResource.password,
                    observedRequest = observedRequest
                )
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
