package com.sympauthy.api.flow

import com.sympauthy.api.FLOW_SURFACE
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.flow.mapper.ClientReferenceResourceMapper
import com.sympauthy.api.flow.mapper.FlowErrorResourceMapper
import com.sympauthy.api.flow.resource.FlowErrorResource
import com.sympauthy.api.flow.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.mapper.InteractiveFlowStepUriMapper
import com.sympauthy.api.util.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.platform.util.orDefault
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("$FLOW_SURFACE/errors")
class ErrorController(
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
    @Inject private val flowErrorResourceMapper: FlowErrorResourceMapper,
    @Inject private val clientMapper: ClientReferenceResourceMapper,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
) {

    @Operation(
        description = "Retrieve why the interactive flow session failed, for the error page to show. A `state` " +
            "that resolves to no interactive flow session is answered the same way, with the code it was " +
            "refused with. So is an authorization request that has already completed, with " +
            "`auth.interactive_flow_session.already_completed`: its authorization code went to the client " +
            "with the request that completed it, and is not handed out again. Any other session that has not " +
            "failed is answered with only `redirect_url`, unless a failure the end-user cannot correct fails " +
            "it on the way, which is then answered as the failure it is.",
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
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): FlowErrorResource =
        interactiveAuthFlowSessionControllerUtil.fetchSessionThenAnswerFailureOrRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            mapFailureToResource = { failure, session ->
                flowErrorResourceMapper.toResource(
                    exception = failure,
                    locale = request.locale.orDefault(),
                    client = session?.let { clientMapper.toResourceOrNull(it) },
                    cancelUrl = session
                        ?.let { stepUriMapper.getCancelUriOrNull(it, authentication.stateOrNull) }
                        ?.toString()
                )
            },
            mapRedirectUriToResource = flowErrorResourceMapper::toResource
        )
}
