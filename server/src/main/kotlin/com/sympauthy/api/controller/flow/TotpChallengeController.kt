package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.FLOW_SURFACE
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.mapper.flow.ClientReferenceResourceMapper
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.api.resource.flow.TotpChallengeFlowResource
import com.sympauthy.api.resource.flow.TotpChallengeInputResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.FLOW_FAILURE_DESCRIPTION
import com.sympauthy.api.util.FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_REDIRECT_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.FLOW_SIGNED_IN_SESSION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionTotpChallengeManager
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("$FLOW_SURFACE/mfa/totp")
class TotpChallengeController(
    @Inject private val challengeManager: InteractiveFlowSessionTotpChallengeManager,
    @Inject private val clientMapper: ClientReferenceResourceMapper,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil
) {

    @Operation(
        description = "Retrieve the MFA challenge step asking the end-user for a TOTP code.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The client the interactive flow session was started for, and the URL leaving it.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION + FLOW_SIGNED_IN_SESSION_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Get
    suspend fun getChallenge(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest
    ): TotpChallengeFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenRun(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            run = { session, _, _ ->
                TotpChallengeFlowResource(
                    client = clientMapper.toResourceOrNull(session),
                    cancelUrl = stepUriMapper.getCancelUriOrNull(session, authentication.stateOrNull)?.toString()
                )
            }
        )

    @Operation(
        description = "Pass the MFA challenge with the TOTP code the end-user's authenticator shows. " +
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
                    "\n- `flow.mfa.totp.challenge.invalid_code`: `code` is absent, or matches none of the TOTP " +
                    "MFA methods the user has enrolled.\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_HEADER_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ],
        tags = ["flow"]
    )
    @Post
    suspend fun submitChallenge(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body inputResource: TotpChallengeInputResource
    ): SimpleFlowResource =
        interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionWithUserThenUpdateAndRedirect(
            state = authentication.stateOrNull,
            observedRequest = observedRequest,
            update = { session, _, user ->
                challengeManager.validateTotpChallenge(session, user, inputResource.code)
            },
            mapRedirectUriToResource = { redirectUri -> SimpleFlowResource(redirectUri.toString()) }
        )
}
