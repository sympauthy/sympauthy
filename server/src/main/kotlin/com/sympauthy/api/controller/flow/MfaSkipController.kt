package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.MfaSkipController.Companion.MFA_SKIP_ENDPOINT
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller(MFA_SKIP_ENDPOINT)
class MfaSkipController(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil
) {

    companion object {
        const val MFA_SKIP_ENDPOINT = "/api/v1/flow/mfa/skip"
    }

    @Operation(
        description = """
Skips the optional MFA step and advances to the next step of the authorization flow.

Only meaningful when MFA is not required (`mfa.required=false`) and the end-user has at least
one enrolled method. The MFA step is marked as resolved so the flow does not prompt again.
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "MFA skipped. Contains the redirect URL to the next step of the authorization flow.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun skipMfa(
        authentication: Authentication
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { authorizeAttempt, _ ->
                authorizeAttemptManager.setMfaPassed(authorizeAttempt)
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
