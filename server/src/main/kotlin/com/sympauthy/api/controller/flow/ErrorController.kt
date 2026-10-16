package com.sympauthy.api.controller.flow

import com.sympauthy.api.mapper.flow.FlowErrorResourceMapper
import com.sympauthy.api.resource.flow.FlowErrorResource
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.interactiveFlowSessionAlreadyCompletedExceptionOf
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("/api/v1/flow/errors")
class ErrorController(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val flowErrorResourceMapper: FlowErrorResourceMapper,
) {

    @Operation(
        description = "Retrieve why the interactive flow session failed, for the error page to show. A `state` " +
            "that resolves to no interactive flow session is answered the same way, with the code it was " +
            "refused with. So is an authorization request that has already completed, with " +
            "`auth.interactive_flow_session.already_completed`: its authorization code went to the client " +
            "with the request that completed it, and is not handed out again. Any other session that has not " +
            "failed is answered with only `redirect_url`.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The failure, or the `redirect_url` to send the browser to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getError(
        request: HttpRequest<*>,
        authentication: Authentication
    ): FlowErrorResource {
        val verifyResult = sessionManager.verifyEncodedInternalState(authentication.stateOrNull)

        return when (verifyResult) {
            is SuccessVerifyEncodedStateResult -> {
                when (val session = verifyResult.session) {
                    is FailedInteractiveFlowSession -> toErrorResource(
                        request = request,
                        detailsId = session.errorDetailsId,
                        descriptionId = session.errorDescriptionId,
                        values = session.errorValues.orEmpty()
                    )

                    else -> {
                        val flow = interactiveAuthFlowSessionManager.findById(
                            session.flowId
                        )
                        val (steppedSession, step) = engine.advance(session)
                        if (step == InteractiveFlowStep.AlreadyCompleted) {
                            // The step leads to this page, so it is answered here rather than redirected to.
                            val alreadyCompleted = interactiveFlowSessionAlreadyCompletedExceptionOf()
                            toErrorResource(
                                request = request,
                                detailsId = alreadyCompleted.detailsId,
                                descriptionId = alreadyCompleted.descriptionId,
                                values = alreadyCompleted.values
                            )
                        } else {
                            val redirectUri = stepUriMapper.toRedirectUri(
                                session = steppedSession,
                                flow = flow,
                                step = step
                            )
                            flowErrorResourceMapper.toResource(redirectUri)
                        }
                    }
                }
            }

            is FailedVerifyEncodedStateResult -> toErrorResource(
                request = request,
                detailsId = verifyResult.detailsId,
                descriptionId = verifyResult.descriptionId,
                values = verifyResult.values
            )
        }
    }

    /**
     * Render the failure named by [detailsId] and [descriptionId] against the locale [request] asks
     * for, interpolating [values] into both messages.
     *
     * Stated once rather than at every branch that reaches it: a failure carries the values its messages
     * name, and a branch rebuilding the exception on its own is a branch that can be written without
     * them.
     */
    private fun toErrorResource(
        request: HttpRequest<*>,
        detailsId: String,
        descriptionId: String?,
        values: Map<String, String>
    ): FlowErrorResource {
        val exception = BusinessException(
            recoverable = false,
            detailsId = detailsId,
            descriptionId = descriptionId,
            values = values
        )
        return flowErrorResourceMapper.toResource(exception, request.locale.orDefault())
    }
}
