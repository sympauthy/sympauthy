package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

/**
 * Lets the end-user cancel the ongoing interactive flow. Works for any flow: the session is marked cancelled
 * and the end-user is handed back to the flow's initiator on the cancellation redirect (the OAuth2
 * `error=access_denied` response for an OAuth2 authorization, or the caller-provided cancel URI for a
 * standalone flow).
 */
@Secured(HAS_STATE)
@Controller("/api/v1/flow/cancel")
class CancelController(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = """
Cancels the ongoing interactive flow identified by the `state`.

Marks the session as cancelled and returns the redirect URL the end-user must be sent to: for an OAuth2
authorization this is the client `redirect_uri` with the `error=access_denied` response; for a flow started
with a cancellation URI it is that URI. A recoverable 4xx error is returned when the flow offers no
cancellation target.
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description =
                    "The flow was cancelled. Contains the redirect URL to hand the end-user back to the initiator.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "The flow is not ongoing or offers no cancellation target."
            )
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun cancel(
        authentication: Authentication
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { session, _ -> sessionManager.markAsCancelled(session) },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
