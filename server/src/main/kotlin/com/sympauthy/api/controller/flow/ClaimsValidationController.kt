package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.FLOW_SURFACE
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.exception.recoverableHttpExceptionOf
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.mapper.flow.ClaimsValidationFlowResultResourceMapper
import com.sympauthy.api.resource.flow.*
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.FLOW_SIGNED_IN_SESSION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionClaimValidationManager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import com.sympauthy.util.enumValueOfWireNameOrNull
import com.sympauthy.util.wireName
import io.micronaut.http.HttpStatus.BAD_REQUEST
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("$FLOW_SURFACE/claims/validation")
class ClaimsValidationController(
    @Inject private val claimValidationManager: InteractiveAuthFlowSessionClaimValidationManager,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
    @Inject private val resourceMapper: ClaimsValidationFlowResultResourceMapper
) {

    @Operation(
        description = "Retrieve the claims validation step for one media: the validation code the end-user is " +
            "to enter, sent first if none was sent yet. A code already sent is not sent again; " +
            "`POST /api/v1/flow/claims/validation/resend` sends a new one. When no claim is left to validate " +
            "through `media`, the response carries only `redirect_url`. " + FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The validation code expected, or the `redirect_url` to send the browser to instead.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION + "\n" + UNKNOWN_MEDIA_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get("/{media}")
    suspend fun getValidationCodeToCollectForMedia(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Parameter(
            description = "Media to validate claims through.",
            schema = Schema(type = "string", allowableValues = ["email", "sms"])
        ) media: String,
    ): ClaimsValidationFlowResource {
        val validationCodeMedia = toMedia(media)
        return interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenRunAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, _, user ->
                claimValidationManager.getOrSendValidationCode(
                    session = session,
                    user = user,
                    media = validationCodeMedia,
                )
            },
            mapResultToResource = resourceMapper::toFlowResource,
            mapRedirectUriToResource = {
                resourceMapper.toFlowResource(
                    media = validationCodeMedia,
                    redirectUri = it
                )
            }
        )
    }

    @Operation(
        description = "Validate the claims a validation code was sent for, with the code the end-user entered. " +
            FLOW_FAILURE_DESCRIPTION,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = FLOW_REDIRECT_RESPONSE_DESCRIPTION,
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION +
                    "\n- `flow.claim_validation.invalid_code`: `code` matches no validation code sent through " +
                    "`media` during this interactive flow session.\n" +
                    "- `flow.claim_validation.expired_code`: `code` matches a validation code that has expired.\n" +
                    UNKNOWN_MEDIA_BAD_REQUEST_DESCRIPTION + "\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun validate(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: ClaimValidationInputResource
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, _, user ->
                claimValidationManager.validateClaimsByCode(
                    session = session,
                    user = user,
                    media = toMedia(inputResource.media),
                    code = inputResource.code
                )
                session
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )

    @Operation(
        description = "Send a new validation code through a media, replacing the one sent through it earlier " +
            "in this interactive flow session. Nothing is sent, and `resent` is `false`, when no code was " +
            "sent through it yet, when the earlier code has neither expired nor reached its `resend_date`, or " +
            "when no claim is left to validate through it.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Whether a new validation code was sent, and that code when it was.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION + FLOW_SIGNED_IN_SESSION_BAD_REQUEST_DESCRIPTION +
                    "\n" + UNKNOWN_MEDIA_BAD_REQUEST_DESCRIPTION + "\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post("/resend")
    suspend fun resendValidationCodes(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: ResendClaimsValidationInputResource
    ): ResendClaimsValidationCodeResultResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenRun(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, _, user ->
                val media = toMedia(inputResource.media)

                val result = claimValidationManager.resendValidationCode(
                    session = session,
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

    /**
     * The media [media] names, in the spelling the flow resources publish it in.
     *
     * A value naming none throws the recoverable `flow.claim_validation.unknown_media`: the caller can send
     * another. The failure is an `api` one, so `handleException` never sees it and the interactive flow
     * session survives it.
     */
    private fun toMedia(media: String): ValidationCodeMedia =
        enumValueOfWireNameOrNull<ValidationCodeMedia>(media) ?: throw recoverableHttpExceptionOf(
            BAD_REQUEST, "flow.claim_validation.unknown_media", "description.flow.claim_validation.unknown_media",
            "media" to media,
            "supportedValues" to ValidationCodeMedia.entries.joinToString(", ") { it.wireName }
        )

    private companion object {
        const val UNKNOWN_MEDIA_BAD_REQUEST_DESCRIPTION =
            "- `flow.claim_validation.unknown_media`: `media` names no media a validation code is sent through."
    }
}
