package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.api.resource.flow.TotpEnrollDataFlowResource
import com.sympauthy.api.resource.flow.TotpEnrollInputResource
import com.sympauthy.business.manager.flow.mfa.WebAuthorizationFlowTotpEnrollmentManager
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("/api/v1/flow/mfa/totp/enroll")
class TotpEnrollmentController(
    @Inject private val enrollmentManager: WebAuthorizationFlowTotpEnrollmentManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil
) {

    @Operation(
        description = """
Initiates TOTP enrollment for the authenticated end-user.

Returns the otpauth:// URI to be rendered as a QR code and the raw base32 secret for manual entry.
Any previously unconfirmed enrollment for the user is discarded and replaced with a fresh secret.
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Enrollment data containing the QR code URI and the raw secret.",
                useReturnTypeSchema = true
            )
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getEnrollmentData(
        authentication: Authentication
    ): TotpEnrollDataFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptWithUserThenRun(
            state = authentication.stateOrNull,
            run = { _, _, user ->
                val data = enrollmentManager.getEnrollmentData(user)
                TotpEnrollDataFlowResource(uri = data.uri, secret = data.secret)
            }
        )

    @Operation(
        description = """
Confirms TOTP enrollment by validating the first code entered by the end-user from their authenticator app.

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
    suspend fun confirmEnrollment(
        authentication: Authentication,
        @Body inputResource: TotpEnrollInputResource
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptWithUserThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            update = { authorizeAttempt, _, user ->
                enrollmentManager.confirmEnrollment(authorizeAttempt, user, inputResource.code.orEmpty())
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
