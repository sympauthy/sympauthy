package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.resource.flow.MfaEnrollmentFlowResource
import com.sympauthy.api.resource.flow.MfaEnrollmentInputResource
import com.sympauthy.api.resource.flow.MfaFlowResource
import com.sympauthy.api.resource.flow.MfaMethodResource
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.manager.flow.mfa.MfaAutoRedirect
import com.sympauthy.business.manager.flow.mfa.MfaMethodSelection
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.SecurityRule.IS_USER
import com.sympauthy.security.clientId
import com.sympauthy.security.stateOrNull
import com.sympauthy.security.userId
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

/**
 * Controller owning MFA enrollment entry points:
 * - starting a standalone enrollment for a signed-in end-user, outside of an OAuth2 authorization (secured by
 *   the end-user's access token).
 * - skipping an optional enrollment offered during a flow (secured by the session state).
 *
 * Starting an enrollment creates an [com.sympauthy.business.model.flow.InteractiveFlowPurpose.MFA_ENROLLMENT]
 * session for the authenticated user and hands the UI the initial `state` + the enrollment page URL; once the
 * enrollment completes, the end-user is redirected back to the caller-provided `return_uri`.
 */
@Secured(IS_USER)
@Controller(MfaEnrollmentController.MFA_ENROLLMENT_ENDPOINT)
class MfaEnrollmentController(
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
) {

    @Operation(
        description = """
Starts a standalone MFA enrollment for the authenticated end-user.

Validates `return_uri` against the calling client's registered redirect URIs, creates an enrollment session,
and returns the signed `state` and the `redirect_url` the UI must navigate the browser to. Once enrollment
completes, the end-user is redirected to `return_uri`.
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The enrollment session was started.",
                useReturnTypeSchema = true
            )
        ]
    )
    @Post
    suspend fun startEnrollment(
        authentication: Authentication,
        @Body resource: MfaEnrollmentInputResource
    ): MfaEnrollmentFlowResource {
        // Validate the return URI against the token's client registered redirect URIs to avoid open redirects.
        val client = clientManager.findClientById(authentication.clientId)
        val returnUri = interactiveAuthFlowSessionManager.parseRequestedRedirectUri(client, resource.returnUri)

        val flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow()
        val session = mfaEnrollmentManager.startMfaEnrollmentSession(
            userId = authentication.userId,
            returnUri = returnUri,
            flow = flow
        )

        val (steppedSession, step) = engine.getCurrentStep(session)
        val redirectUri = stepUriMapper.toRedirectUri(steppedSession, flow, step)
        return MfaEnrollmentFlowResource(
            state = sessionManager.encodeState(steppedSession),
            redirectUrl = redirectUri.toString()
        )
    }

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
    @Secured(HAS_STATE)
    @Get
    suspend fun getEnrollmentSelection(
        authentication: Authentication
    ): MfaFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenRun(
            state = authentication.stateOrNull,
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
    @Secured(HAS_STATE)
    @Get("/skip")
    suspend fun skipEnrollment(
        authentication: Authentication
    ): HttpResponse<*> =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect(
            state = authentication.stateOrNull,
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
