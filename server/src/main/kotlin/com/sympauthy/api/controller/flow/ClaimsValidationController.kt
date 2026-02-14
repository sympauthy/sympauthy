package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.util.WebAuthorizationFlowControllerUtil
import com.sympauthy.api.mapper.flow.ClaimsValidationFlowResultResourceMapper
import com.sympauthy.api.resource.flow.*
import com.sympauthy.business.manager.flow.WebAuthorizationFlowClaimValidationManager
import com.sympauthy.business.model.code.ValidationCodeMedia
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
@Controller("/api/v1/flow/claims/validation")
class ClaimsValidationController(
    @Inject private val claimValidationManager: WebAuthorizationFlowClaimValidationManager,
    @Inject private val webAuthorizationFlowControllerUtil: WebAuthorizationFlowControllerUtil,
    @Inject private val resourceMapper: ClaimsValidationFlowResultResourceMapper
) {

    @Operation(
        method = "Get validation code expected from end-user through the media",
        description = """
Return information about the validation code that the end-user will receive or have received through the provided media
to validate the claims collected earlier.

If a code is required but not have been sent yet, send the code to validate the claims populated by the user earlier 
through the media. ex. this authorization server will send an email to the email claim of the user to validate 
it has access to the box.

If there is no more validation code expected to be sent through the media by the authorization server, 
the response will contain the redirect URL where the user must be redirected to continue the authorization flow.

To avoid spamming the user, if a validation code has already been sent to the user through the media,
the code will not be sent again but it will still be present in the output.

The dedicated operation "Resend claim validation code" allows the user to ask for another code in case they did not
receive the previous one.
""",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = """
Result containing either:
- information about the code expected from the end-user.
- a redirect URL for the end-user to continue the authorization flow.
""",
            ),
        ],
        tags = ["flow"]
    )
    @Get("/{media}")
    suspend fun getValidationCodeToCollectForMedia(
        authentication: Authentication,
        media: ValidationCodeMedia,
    ): ClaimsValidationFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptWithUserThenRunAndRedirect(
            authentication.stateOrNull,
            run = { authorizeAttempt, _, user ->
                claimValidationManager.getOrSendValidationCode(
                    authorizeAttempt = authorizeAttempt,
                    user = user,
                    media = media,
                )
            },
            mapResultToResource = resourceMapper::toFlowResource,
            mapRedirectUriToResource = {
                resourceMapper.toFlowResource(
                    media = media,
                    redirectUri = it
                )
            }
        )

    fun getMedia(media: String): ValidationCodeMedia {
        return ValidationCodeMedia.valueOf(media)
    }

    @Operation(
        method = "Validate the code sent through a media",
        description = "Validate the code entered by the user.",
        tags = ["flow"]
    )
    @Post
    suspend fun validate(
        authentication: Authentication,
        @Body inputResource: ClaimValidationInputResource
    ): SimpleFlowResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptThenRunAndRedirect(
            state = authentication.stateOrNull,
            run = { authorizeAttempt, _ ->
                claimValidationManager.validateClaimsByCode(
                    authorizeAttempt = authorizeAttempt,
                    media = ValidationCodeMedia.valueOf(inputResource.media),
                    code = inputResource.code
                )
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )

    @Operation(
        method = "Resend code sent through a media",
        description =
            """
Ask this authorization server to send through the media in the body new codes  to validates the claims populated by the 
end-user earlier.

This authorization server will not send new validation code in the following cases:
- To avoid spamming.
- The claims have already been validated.
- No claim require validation through this media.
""",
        tags = ["flow"]
    )
    @Post("/resend")
    suspend fun resendValidationCodes(
        authentication: Authentication,
        @Body inputResource: ResendClaimsValidationInputResource
    ): ResendClaimsValidationCodeResultResource =
        webAuthorizationFlowControllerUtil.fetchOnGoingAttemptWithUserThenRun(
            state = authentication.stateOrNull,
            run = { authorizeAttempt, _, user ->
                val media = getMedia(inputResource.media)

                val result = claimValidationManager.resendValidationCode(
                    authorizeAttempt = authorizeAttempt,
                    user = user,
                    media = media
                )
                val resent = result.resent

                resourceMapper.toResendResultResource(
                    media = media,
                    resent = resent,
                    newValidationCode = if (resent) result.validationCode else null,
                )
            }
        )
}
