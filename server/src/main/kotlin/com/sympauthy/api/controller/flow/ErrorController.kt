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
                    is FailedInteractiveFlowSession -> {
                        val exception = BusinessException(
                            recoverable = false,
                            detailsId = session.errorDetailsId,
                            descriptionId = session.errorDescriptionId,
                        )
                        flowErrorResourceMapper.toResource(exception, request.locale.orDefault())
                    }

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

            is FailedVerifyEncodedStateResult -> {
                val exception = BusinessException(
                    recoverable = false,
                    detailsId = verifyResult.detailsId,
                    descriptionId = verifyResult.descriptionId
                )
                flowErrorResourceMapper.toResource(exception, request.locale.orDefault())
            }
        }
    }
}
