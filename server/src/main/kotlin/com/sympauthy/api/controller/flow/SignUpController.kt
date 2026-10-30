package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.FLOW_SURFACE
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.mapper.CollectedClaimUpdateMapper
import com.sympauthy.api.mapper.flow.CollectableClaimResourceMapper
import com.sympauthy.api.resource.flow.PasswordResource
import com.sympauthy.api.resource.flow.SignUpFlowResource
import com.sympauthy.api.resource.flow.SignUpInputResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionPasswordManager
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.util.orDefault
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
                    buildSignUpConfiguration(session, flow, locale)
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
        locale: Locale
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
            signInRedirectUrl = signInRedirectUrl
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
