package com.sympauthy.api.flow

import com.sympauthy.api.FLOW_SURFACE
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.flow.mapper.ClientReferenceResourceMapper
import com.sympauthy.api.flow.resource.MfaFlowResource
import com.sympauthy.api.flow.resource.MfaMethodResource
import com.sympauthy.api.flow.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_SIGNED_IN_SESSION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.flow.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.mapper.InteractiveFlowStepUriMapper
import com.sympauthy.api.util.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.flow.mfa.manager.InteractiveFlowSessionMfaChallengeManager
import com.sympauthy.business.flow.mfa.manager.MfaAutoRedirect
import com.sympauthy.business.flow.mfa.manager.MfaMethodSelection
import com.sympauthy.business.securitycontext.model.ObservedRequest
import com.sympauthy.platform.util.wireName
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("$FLOW_SURFACE/mfa/challenge")
class MfaChallengeController(
    @Inject private val mfaChallengeManager: InteractiveFlowSessionMfaChallengeManager,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val clientMapper: ClientReferenceResourceMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = "Retrieve the MFA challenge step: the MFA methods the end-user can prove themselves with. " +
            "Unless the user has enrolled more than one, the response carries only `redirect_url`, leading to " +
            "the challenge of the one they hold. The challenge cannot be skipped.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The MFA methods to choose between, or the `redirect_url` of the one method's step.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION + FLOW_SIGNED_IN_SESSION_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getChallengeRedirect(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): MfaFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenRun(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, flow, user ->
                when (val result = mfaChallengeManager.getChallengeRoutingResult(user)) {
                    is MfaAutoRedirect -> MfaFlowResource(
                        redirectUrl = stepUriMapper.toRedirectUri(session, flow, result.step).toString()
                    )
                    is MfaMethodSelection -> MfaFlowResource(
                        methods = result.methods.map {
                            MfaMethodResource(
                                method = it.type.wireName,
                                redirectUrl = stepUriMapper.toRedirectUri(session, flow, it.step).toString()
                            )
                        },
                        client = clientMapper.toResourceOrNull(session),
                        cancelUrl = stepUriMapper.getCancelUriOrNull(session, authentication.stateOrNull)?.toString()
                    )
                }
            }
        )
}
