package com.sympauthy.api.controller.client

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.resource.client.ClientMfaEnrollmentInputResource
import com.sympauthy.api.resource.client.ClientMfaEnrollmentResource
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.security.SecurityRule.CLIENT_USERS_MFA_WRITE
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject

/**
 * Client-API entry point for a client to start enrolling a multi-factor authentication method on behalf of one
 * of its signed-in end-users, outside of an OAuth2 authorization (e.g. from an account-settings screen).
 *
 * Dual authentication:
 * - the **calling client** authenticates with a client-credentials token holding the `users:mfa:write` scope.
 * - the **end-user** is identified by their access token, passed in the request body and validated to have been
 *   issued to the calling client.
 *
 * It starts an [com.sympauthy.business.model.flow.InteractiveFlowPurpose.MFA_ENROLLMENT] session for that
 * end-user and hands the caller the initial signed `state` + the enrollment page URL to drive the browser to.
 * Once the enrollment completes, the end-user is redirected back to the caller-provided `return_uri`. The
 * subsequent enrollment steps are driven through the state-secured flow API.
 */
@Secured(CLIENT_USERS_MFA_WRITE)
@Controller(ClientMfaEnrollmentController.CLIENT_MFA_ENROLLMENT_ENDPOINT)
class ClientMfaEnrollmentController(
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val clientRedirectUriManager: ClientRedirectUriManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
) {

    @Operation(
        description = """
Starts a standalone MFA enrollment for one of the calling client's signed-in end-users.

Validates the end-user `access_token` (must be a valid, unexpired user access token issued to the calling
client) and the `return_uri` (must be one of the calling client's registered redirect URIs), creates an
enrollment session, and returns the signed `state` and the `redirect_url` the caller must navigate the
end-user's browser to. Once enrollment completes, the end-user is redirected to `return_uri`.
        """,
        tags = ["client"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The enrollment session was started.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid access token or return URI, or MFA is not enabled."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid client access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: users:mfa:write."
            )
        ]
    )
    @Post
    @Secured(CLIENT_USERS_MFA_WRITE)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.USERS_MFA_WRITE])
    suspend fun startEnrollment(
        authentication: Authentication,
        @Body resource: ClientMfaEnrollmentInputResource
    ): ClientMfaEnrollmentResource {
        // Fail fast (before creating any session) when MFA is not enabled on this server.
        if ((uncheckedMfaConfig as? EnabledMfaConfig)?.enabled != true) {
            throw recoverableBusinessExceptionOf(
                "client.mfa.enrollment.mfa_disabled",
                "description.client.mfa.enrollment.mfa_disabled"
            )
        }

        val client = clientManager.findClientById(authentication.clientAuthentication.clientId)

        // Validate the end-user access token: signature, expiry, not revoked, issued to the calling client
        // (enforced by introspectToken), and associated with an end-user (not a client-credentials token).
        val userToken = resource.accessToken
            ?.let { tokenManager.introspectToken(client, it, "access_token") }
        val userId = userToken?.userId
            ?: throw recoverableBusinessExceptionOf(
                "client.mfa.enrollment.invalid_access_token",
                "description.client.mfa.enrollment.invalid_access_token"
            )

        // Validate the return URI (and the optional cancel URI) against the calling client's registered
        // redirect URIs to avoid open redirects. recoverable = true: a bad URI is a bad request from the
        // calling client (400), not a server error.
        val returnUri = clientRedirectUriManager.parseRequestedRedirectUri(
            client,
            resource.returnUri,
            recoverable = true
        )
        val cancelUri = resource.cancelUri
            ?.let { clientRedirectUriManager.parseRequestedRedirectUri(client, it, recoverable = true) }

        val flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow()
        val session = mfaEnrollmentManager.startMfaEnrollmentSession(
            userId = userId,
            returnUri = returnUri,
            flow = flow,
            initiatingClientId = client.id,
            cancelUri = cancelUri
        )

        val (steppedSession, step) = engine.advance(session)
        val redirectUri = stepUriMapper.toRedirectUri(steppedSession, flow, step)
        return ClientMfaEnrollmentResource(
            state = sessionManager.encodeState(steppedSession),
            redirectUrl = redirectUri.toString()
        )
    }

    companion object {
        const val CLIENT_MFA_ENROLLMENT_ENDPOINT = "/api/v1/client/mfa/enrollment"
    }
}
