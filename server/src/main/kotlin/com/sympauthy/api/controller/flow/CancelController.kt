package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.FLOW_SURFACE
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

/**
 * Lets a person leave an interactive flow from a page offering it: a session still running ends as cancelled,
 * and the person is sent back to whoever started it.
 *
 * **It ends a session on a `GET`, and answers with a redirect rather than a `redirect_url`**, departing from
 * `docs/standards/api-standard.md` so that a page leaves by navigating to a URL, without sending a request
 * first. Why that is safe here, and what it asks of the page, is `docs/design/interactive-flow.md`.
 */
@Secured(HAS_STATE)
@Controller(CancelController.FLOW_CANCEL_ENDPOINT)
class CancelController(
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = "Leave the interactive flow session, and send the browser back to whoever started it. A " +
            "session still running ends as cancelled; one that has already ended keeps how it ended. Every step " +
            "links here as `cancel_url` where leaving is possible.",
        responses = [
            ApiResponse(
                responseCode = "303",
                description = "Redirects the browser:\n" +
                    "- for an authorization request, to its `redirect_uri` with `error` set to `access_denied`, " +
                    "or to the error answering why it failed, and with `state` where the request carried one.\n" +
                    "- for a flow a client or an administrator started, to the URL it returns the end-user to, " +
                    "with `sympauthy_flow` naming the flow, `sympauthy_flow_error` naming how it ended where it " +
                    "did not complete, and `sympauthy_return_state` where the request starting it carried a " +
                    "`return_state`.\n" +
                    "- to the error page of the flow, where the `state` resolves to no interactive flow session, " +
                    "or the session offers no way out: an authorization request that has already completed, or " +
                    "one whose client or `redirect_uri` was refused."
            ),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun cancel(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): HttpResponse<*> = HttpResponse.seeOther<Any>(
        interactiveAuthFlowSessionControllerUtil.fetchSessionThenLeave(
            state = authentication.stateOrNull,
            observedRequest = observedRequest
        )
    )

    companion object {
        const val FLOW_CANCEL_ENDPOINT = "$FLOW_SURFACE/cancel"
    }
}
