package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.api.resource.flow.TotpChallengeInputResource
import com.sympauthy.business.manager.flow.mfa.WebAuthorizationFlowTotpChallengeManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("/api/v1/flow/mfa/totp")
class TotpChallengeController(
    @Inject private val challengeManager: WebAuthorizationFlowTotpChallengeManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil
) {

    @Operation(
        description = """
Validates the TOTP code submitted by the end-user to complete the MFA step of the authorization flow.

On success, the end-user is redirected to the next step of the authorization flow.
On failure, a recoverable 4xx error is returned so the end-user can retry with their next code.
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Code was valid. Contains the redirect URL to continue the authorization flow.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "Code was invalid. The end-user may retry with their next code."
            )
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun submitChallenge(
        authentication: Authentication,
        @Body inputResource: TotpChallengeInputResource
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptWithUserThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { authorizeAttempt, _, user ->
                challengeManager.validateTotpChallenge(authorizeAttempt, user, inputResource.code)
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
