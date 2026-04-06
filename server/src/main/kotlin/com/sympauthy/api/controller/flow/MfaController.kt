package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.MfaSkipController.Companion.MFA_SKIP_ENDPOINT
import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.resource.flow.MfaFlowResource
import com.sympauthy.api.resource.flow.MfaMethodResource
import com.sympauthy.business.manager.flow.mfa.MfaAutoRedirect
import com.sympauthy.business.manager.flow.mfa.MfaMethodSelection
import com.sympauthy.business.manager.flow.mfa.WebAuthorizationFlowMfaManager
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
@Controller("/api/v1/flow/mfa")
class MfaController(
    @Inject private val mfaManager: WebAuthorizationFlowMfaManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil
) {

    @Operation(
        description = """
Routes the end-user to the appropriate MFA sub-step of the authorization flow.

Returns one of two response shapes depending on the situation:

**Auto-redirect** — only `redirect_url` is present, the UI must follow it without showing a screen:
- MFA required, no method enrolled → redirects to TOTP enrollment
- MFA required, one method enrolled → redirects to TOTP challenge

**Method selection** — `methods` is present, the UI must render a selection screen:
- MFA optional, one or more methods enrolled → `methods` list plus a `skip_redirect_url`
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Routing decision for the MFA step.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getMfaRedirect(
        authentication: Authentication
    ): MfaFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptWithUserThenRun(
            state = authentication.stateOrNull,
            run = { authorizeAttempt, flow, user ->
                when (val result = mfaManager.getMfaResult(authorizeAttempt, user, flow, MFA_SKIP_ENDPOINT)) {
                    is MfaAutoRedirect -> MfaFlowResource(redirectUrl = result.uri.toString())
                    is MfaMethodSelection -> MfaFlowResource(
                        methods = result.methods.map {
                            MfaMethodResource(method = it.name, redirectUrl = it.uri.toString())
                        },
                        skipRedirectUrl = result.skipUri?.toString()
                    )
                }
            }
        )
}
