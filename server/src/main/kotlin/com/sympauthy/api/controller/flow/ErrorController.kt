package com.sympauthy.api.controller.flow

import com.sympauthy.api.mapper.flow.FlowErrorResourceMapper
import com.sympauthy.api.resource.flow.FlowErrorResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
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
        description = "Get details about the error that caused the authentication flow to fail.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = """
Result containing either:
- details about the error that caused the authentication flow to fail.
- a redirect uri for the end-user to continue the authorization flow if there is no error.
""",
            ),
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
                        val redirectUri = stepUriMapper.toRedirectUri(
                            session = steppedSession,
                            flow = flow,
                            step = step
                        )
                        flowErrorResourceMapper.toResource(redirectUri)
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
     * Stated once rather than at each of the two branches that reach it: a failure carries the values
     * its messages name, and a branch rebuilding the exception on its own is a branch that can be
     * written without them.
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
