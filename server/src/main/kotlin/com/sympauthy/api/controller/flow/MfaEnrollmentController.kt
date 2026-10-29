package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.FLOW_SURFACE
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.resource.flow.MfaFlowResource
import com.sympauthy.api.resource.flow.MfaMethodResource
import com.sympauthy.api.util.FLOW_FAILURE_SEE_OTHER_DESCRIPTION
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_SEE_OTHER_DESCRIPTION
import com.sympauthy.api.util.FLOW_SIGNED_IN_SESSION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.manager.flow.mfa.MfaAutoRedirect
import com.sympauthy.business.manager.flow.mfa.MfaMethodSelection
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.util.wireName
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
 * Controller owning the MFA enrollment steps of an interactive flow (secured by the session state):
 * - the enrollment method-selection step (choose the method to enroll, and skip when optional).
 * - skipping an optional enrollment offered during a flow.
 */
@Secured(HAS_STATE)
@Controller(MfaEnrollmentController.MFA_ENROLLMENT_ENDPOINT)
class MfaEnrollmentController(
    @Inject private val mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
) {

    @Operation(
        description = "Retrieve the MFA enrollment step: the MFA methods the end-user can enroll, and " +
            "`skip_redirect_url` when the enrollment is optional. When it is not, the response carries only " +
            "`redirect_url`, leading to the enrollment of the one MFA method to enroll.",
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
    suspend fun getEnrollmentSelection(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): MfaFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenRun(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, flow, _ ->
                when (val result = mfaEnrollmentManager.getEnrollmentRoutingResult(session)) {
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
                        skipRedirectUrl = if (result.skippable) {
                            stepUriMapper.getMfaSkipUri(session, MFA_ENROLLMENT_SKIP_ENDPOINT).toString()
                        } else null
                    )
                }
            }
        )

    @Operation(
        description = "Skip the optional MFA enrollment and continue the flow, following " +
            "`skip_redirect_url`. Skipping an enrollment that is not optional, or one for a user already " +
            "holding an MFA method, is a failure the end-user cannot correct. " +
            FLOW_FAILURE_SEE_OTHER_DESCRIPTION,
        responses = [
            ApiResponse(responseCode = "303", description = FLOW_SEE_OTHER_DESCRIPTION),
            ApiResponse(responseCode = "400", description = FLOW_STATE_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get("/skip")
    suspend fun skipEnrollment(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): HttpResponse<*> =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, _ ->
                mfaEnrollmentManager.skipMfa(session)
            },
            mapRedirectUriToResource = { redirectUri -> HttpResponse.seeOther<Any>(redirectUri) }
        )

    companion object {
        const val MFA_ENROLLMENT_ENDPOINT = "$FLOW_SURFACE/mfa/enrollment"
        const val MFA_ENROLLMENT_SKIP_ENDPOINT = "$MFA_ENROLLMENT_ENDPOINT/skip"
    }
}
