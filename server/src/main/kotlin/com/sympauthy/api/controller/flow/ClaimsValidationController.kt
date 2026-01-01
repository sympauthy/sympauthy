package com.sympauthy.api.controller.flow

import com.sympauthy.api.mapper.flow.ClaimsValidationFlowResultResourceMapper
import com.sympauthy.api.resource.flow.*
import com.sympauthy.api.util.flow.FlowControllerHelper
import com.sympauthy.business.manager.flow.AuthorizationFlowClaimValidationManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.security.SecurityRule.HAS_STATE
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
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val claimValidationManager: AuthorizationFlowClaimValidationManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val resourceMapper: ClaimsValidationFlowResultResourceMapper,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder,
    @Inject private val flowControllerHelper: FlowControllerHelper
) {

    @Operation(
        method = "Send validation code through the media",
        description = """
Send the code to validate the claims populated by the user earlier through the media.
ex. this authorization server will send an email to the email claim of the user to validate it has access to the box.

If there is no more validation code expected to be sent through the media by the authorization server, 
the response will contain the redirect uri where the user must be redirected to continue the authorization flow.

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
- a redirect uri for the end-user to continue the authorization flow.
""",
            ),
        ],
        tags = ["flow"]
    )
    @Get("/{media}")
    suspend fun getValidationCodeToCollectForMedia(
        authentication: Authentication,
        media: ValidationCodeMedia,
    ): ClaimsValidationFlowResultResource =
        webAuthorizationFlowManager.extractFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, flow ->
            val user = flowControllerHelper.getUser(authorizeAttempt)

            val collectedClaims = collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt)
            val result = webAuthorizationFlowManager.completeAuthorizationFlowOrRedirect(
                user = user,
                collectedClaims = collectedClaims,
            )

            val validationCode = claimValidationManager.getOrSendValidationCode(
                authorizeAttempt = authorizeAttempt,
                user = user,
                media = media,
            )

            if (validationCode != null) {
                resourceMapper.toFlowResultResource(validationCode)
            } else {
                val redirectUri = redirectUriBuilder.getRedirectUri(
                    authorizeAttempt = authorizeAttempt,
                    flow = flow,
                    result = result,
                )
                resourceMapper.toFlowResultResource(
                    redirectUri = redirectUri,
                    media = media,
                )
            }
        }

    fun getMedia(media: String): ValidationCodeMedia {
        return ValidationCodeMedia.valueOf(media)
    }

    @Operation(
        method = "Validate code",
        description = "Validate the code entered by the user.",
        tags = ["flow"]
    )
    @Post
    suspend fun validate(
        authentication: Authentication,
        @Body inputResource: ClaimValidationInputResource
    ): FlowResultResource =
        webAuthorizationFlowManager.extractFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, flow ->
            val user = flowControllerHelper.getUser(authorizeAttempt)

            claimValidationManager.validateClaimsByCode(
                authorizeAttempt = authorizeAttempt,
                media = ValidationCodeMedia.valueOf(inputResource.media),
                code = inputResource.code
            )

            val collectedClaims = collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt)
            val result = webAuthorizationFlowManager.completeAuthorizationFlowOrRedirect(
                user = user,
                collectedClaims = collectedClaims,
            )

            val redirectUri = redirectUriBuilder.getRedirectUri(
                authorizeAttempt = authorizeAttempt,
                flow = flow,
                result = result
            )
            FlowResultResource(redirectUri.toString())
        }

    @Operation(
        method = "Resend claim validation code",
        description =
            """
Ask this authorization server to send through the media in the body new codes 
to validates the claims populated by the user earlier.

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
        webAuthorizationFlowManager.extractFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, _ ->
            val user = flowControllerHelper.getUser(authorizeAttempt)
            val media = getMedia(inputResource.media)

            val result = claimValidationManager.resendValidationCode(
                authorizeAttempt = authorizeAttempt,
                user = user,
                media = media
            )

            resourceMapper.toResendResultResource(
                media = media,
                resent = result.resent,
                newValidationCode = if (result.resent) result.validationCode else null,
            )
        }
}
