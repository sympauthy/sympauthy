package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.resource.flow.SimpleFlowResource
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

Returns the URL of the next MFA page:
- If the end-user has no confirmed TOTP enrollment, redirects to the TOTP enrollment page.
- If the end-user has a confirmed TOTP enrollment, redirects to the TOTP challenge page.
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Contains the redirect URL to the appropriate MFA sub-step.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getMfaRedirect(
        authentication: Authentication
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptWithUserThenRun(
            state = authentication.stateOrNull,
            run = { authorizeAttempt, flow, user ->
                val redirectUri = mfaManager.getMfaRedirectUri(authorizeAttempt, user, flow)
                SimpleFlowResource(redirectUri.toString())
            }
        )
}
