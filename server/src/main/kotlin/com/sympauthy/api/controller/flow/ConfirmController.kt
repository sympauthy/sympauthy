package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.resource.flow.ConfirmFlowResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.flow.confirm.InteractiveFlowSessionConfirmManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
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
@Controller("/api/v1/flow/confirm")
@Secured(HAS_STATE)
class ConfirmController(
    @Inject private val confirmManager: InteractiveFlowSessionConfirmManager,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = """
Return everything the custom UI needs to render the confirmation step (the action the end-user is asked to
approve and the client that initiated it), or a redirect URL if the end-user is not expected to be on the
confirmation step (ex. the confirmation was already given, or the flow carries no confirmation step).

This configuration only contains information associated to this authorization server, the client and the
on-going flow. All URLs it contains already include the state query param.
        """,
        tags = ["flow"]
    )
    @Get
    suspend fun getConfirmation(
        authentication: Authentication
    ): ConfirmFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRunAndRedirect(
            state = authentication.stateOrNull,
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
                        action = confirm.action.name,
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
        description = "Approve the action the end-user was asked to confirm. The flow then continues to the next step.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The action was approved. Contains the redirect URL to continue the flow.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun confirm(
        authentication: Authentication
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { session, _ ->
                confirmManager.markConfirmed(session)
                session
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
