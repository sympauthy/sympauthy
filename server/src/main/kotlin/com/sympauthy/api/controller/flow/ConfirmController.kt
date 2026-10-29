package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.FLOW_SURFACE
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.resource.flow.ConfirmFlowResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.api.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.business.manager.flow.confirm.InteractiveFlowSessionConfirmManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.util.wireName
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

/**
 * Drives the confirmation step of an interactive flow: the signed-in end-user must explicitly approve an
 * action a client (or an administrator) initiated on their behalf (an
 * [com.sympauthy.business.model.flow.InteractiveFlowPurpose.CONFIRM] gate)
 * before the rest of the flow runs. Denial is handled by cancelling the flow via `POST /api/v1/flow/cancel`.
 */
@Controller("$FLOW_SURFACE/confirm")
@Secured(HAS_STATE)
class ConfirmController(
    @Inject private val confirmManager: InteractiveFlowSessionConfirmManager,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = "Retrieve the confirmation step: the action a client or an administrator asked for on the " +
            "end-user's behalf, for the end-user to approve with `POST /api/v1/flow/confirm` or decline with " +
            "`POST /api/v1/flow/cancel`. When there is no action waiting for approval, the response carries " +
            "only `redirect_url`. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The action to approve, or the `redirect_url` to send the browser to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getConfirmation(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): ConfirmFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, _ ->
                val confirm = confirmManager.fetchConfirmOrNull(session)
                if (confirm != null && !confirm.confirmed) {
                    // Warn the UI up front when a re-authentication step is still ahead in this session (server
                    // -computed, not hardcoded per action): true when the chain carries an unresolved
                    // REAUTHENTICATION purpose (e.g. provider link), false otherwise (e.g. MFA enrollment).
                    val requiresReauthentication =
                        InteractiveFlowPurpose.REAUTHENTICATION in session.purposes &&
                            InteractiveFlowPurpose.REAUTHENTICATION !in session.completedPurposes
                    ConfirmFlowResource(
                        action = confirm.action.wireName,
                        requiresReauthentication = requiresReauthentication,
                        initiatingClientId = confirm.clientId
                    )
                } else {
                    null
                }
            },
            mapResultToResource = { it },
            mapRedirectUriToResource = { ConfirmFlowResource(redirectUrl = it.toString()) }
        )

    @Operation(
        description = "Approve the action the confirmation step asks the end-user about. " +
            FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = FLOW_REDIRECT_RESPONSE_DESCRIPTION,
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun confirm(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, _ ->
                confirmManager.markConfirmed(session)
                session
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
