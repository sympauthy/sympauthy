package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.resource.flow.MfaFlowResource
import com.sympauthy.api.resource.flow.MfaMethodResource
import com.sympauthy.api.util.observedRequest
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.manager.flow.mfa.MfaAutoRedirect
import com.sympauthy.business.manager.flow.mfa.MfaMethodSelection
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
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
        description = """
Routes the end-user to the MFA method they must enroll.

Returns one of two response shapes:

**Auto-redirect** — only `redirect_url` is present, the UI must follow it without showing a screen:
- a single method to enroll and no skip (MFA required, or a standalone client-initiated enrollment) → redirects to TOTP enrollment

**Method selection** — `methods` is present, the UI must render a selection screen:
- the enrollment is optional → `methods` list plus a `skip_redirect_url`
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Routing decision for the MFA enrollment step.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getEnrollmentSelection(
        authentication: Authentication,
        httpRequest: HttpRequest<*>
    ): MfaFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenRun(
            state = authentication.stateOrNull,
            observedRequest = httpRequest.observedRequest(),
            run = { session, flow, _ ->
                when (val result = mfaEnrollmentManager.getEnrollmentRoutingResult(session)) {
                    is MfaAutoRedirect -> MfaFlowResource(
                        redirectUrl = stepUriMapper.toRedirectUri(session, flow, result.step).toString()
                    )
                    is MfaMethodSelection -> MfaFlowResource(
                        methods = result.methods.map {
                            MfaMethodResource(
                                method = it.name,
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
        description = """
Skips the optional MFA enrollment offered during a flow and advances to the next step.

Only meaningful when MFA is not required (`mfa.required=false`) and the enrollment is not a standalone,
client-initiated one. The MFA step is marked as resolved so the flow does not prompt again.
        """,
        responses = [
            ApiResponse(
                responseCode = "303",
                description = "Enrollment skipped. Redirects the browser to the next step of the flow."
            )
        ],
        tags = ["flow"]
    )
    @Get("/skip")
    suspend fun skipEnrollment(
        authentication: Authentication,
        httpRequest: HttpRequest<*>
    ): HttpResponse<*> =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = httpRequest.observedRequest(),
            update = { session, _ ->
                mfaEnrollmentManager.skipMfa(session)
            },
            mapRedirectUriToResource = { redirectUri -> HttpResponse.seeOther<Any>(redirectUri) }
        )

    companion object {
        const val MFA_ENROLLMENT_ENDPOINT = "/api/v1/flow/mfa/enrollment"
        const val MFA_ENROLLMENT_SKIP_ENDPOINT = "$MFA_ENROLLMENT_ENDPOINT/skip"
    }
}
