package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.resource.flow.MfaFlowResource
import com.sympauthy.api.resource.flow.MfaMethodResource
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaChallengeManager
import com.sympauthy.business.manager.flow.mfa.MfaAutoRedirect
import com.sympauthy.business.manager.flow.mfa.MfaMethodSelection
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("/api/v1/flow/mfa/challenge")
class MfaChallengeController(
    @Inject private val mfaChallengeManager: InteractiveFlowSessionMfaChallengeManager,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = """
Routes the end-user to the enrolled MFA method they must challenge.

Returns one of two response shapes:

**Auto-redirect** — only `redirect_url` is present, the UI must follow it without showing a screen:
- a single method is enrolled → redirects to the TOTP challenge

**Method selection** — `methods` is present, the UI must render a selection screen:
- several methods are enrolled → `methods` list (challenges cannot be skipped)
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Routing decision for the MFA challenge step.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getChallengeRedirect(
        authentication: Authentication
    ): MfaFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenRun(
            state = authentication.stateOrNull,
            run = { session, flow, user ->
                when (val result = mfaChallengeManager.getChallengeRoutingResult(user)) {
                    is MfaAutoRedirect -> MfaFlowResource(
                        redirectUrl = stepUriMapper.toRedirectUri(session, flow, result.step).toString()
                    )
                    is MfaMethodSelection -> MfaFlowResource(
                        methods = result.methods.map {
                            MfaMethodResource(
                                method = it.name,
                                redirectUrl = stepUriMapper.toRedirectUri(session, flow, it.step).toString()
                            )
                        }
                    )
                }
            }
        )
}
