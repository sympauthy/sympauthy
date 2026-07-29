package com.sympauthy.api.controller.client

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.resource.flow.MfaEnrollmentFlowResource
import com.sympauthy.api.resource.flow.MfaEnrollmentInputResource
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.security.SecurityRule.IS_USER
import com.sympauthy.security.clientId
import com.sympauthy.security.userId
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

/**
 * Client-API entry point for a signed-in end-user to start enrolling a multi-factor authentication method,
 * outside of an OAuth2 authorization.
 *
 * Secured by the end-user's access token: it starts an
 * [com.sympauthy.business.model.flow.InteractiveFlowPurpose.MFA_ENROLLMENT] session for the authenticated user
 * and hands the UI the initial `state` + the enrollment page URL. Once the enrollment completes, the end-user
 * is redirected back to the caller-provided `return_uri`. The subsequent enrollment steps are then driven
 * through the state-secured flow API.
 */
@Secured(IS_USER)
@Controller(ClientMfaEnrollmentController.CLIENT_MFA_ENROLLMENT_ENDPOINT)
class ClientMfaEnrollmentController(
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
) {

    @Operation(
        description = """
Starts a standalone MFA enrollment for the authenticated end-user.

Validates `return_uri` against the calling client's registered redirect URIs, creates an enrollment session,
and returns the signed `state` and the `redirect_url` the UI must navigate the browser to. Once enrollment
completes, the end-user is redirected to `return_uri`.
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The enrollment session was started.",
                useReturnTypeSchema = true
            )
        ]
    )
    @Post
    suspend fun startEnrollment(
        authentication: Authentication,
        @Body resource: MfaEnrollmentInputResource
    ): MfaEnrollmentFlowResource {
        // Validate the return URI against the token's client registered redirect URIs to avoid open redirects.
        val client = clientManager.findClientById(authentication.clientId)
        val returnUri = interactiveAuthFlowSessionManager.parseRequestedRedirectUri(client, resource.returnUri)

        val flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow()
        val session = mfaEnrollmentManager.startMfaEnrollmentSession(
            userId = authentication.userId,
            returnUri = returnUri,
            flow = flow
        )

        val (steppedSession, step) = engine.getCurrentStep(session)
        val redirectUri = stepUriMapper.toRedirectUri(steppedSession, flow, step)
        return MfaEnrollmentFlowResource(
            state = sessionManager.encodeState(steppedSession),
            redirectUrl = redirectUri.toString()
        )
    }

    companion object {
        const val CLIENT_MFA_ENROLLMENT_ENDPOINT = "/api/v1/client/mfa/enrollment"
    }
}
