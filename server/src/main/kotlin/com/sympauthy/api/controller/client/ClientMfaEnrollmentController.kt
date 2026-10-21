package com.sympauthy.api.controller.client

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.resource.client.ClientMfaEnrollmentInputResource
import com.sympauthy.api.resource.client.ClientMfaEnrollmentResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.RETURN_URI_BAD_REQUEST_DESCRIPTION
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.security.SecurityRule.CLIENT_USERS_MFA_WRITE
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestAttribute
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
    @Inject private val tokenManager: TokenManager,
    @Inject private val userManager: UserManager,
    @Inject private val mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
) {

    @Operation(
        description = "Start enrolling an MFA method for the user `access_token` was issued for. Send the " +
            "end-user's browser to `redirect_url`: the end-user confirms the enrollment, enrolls the MFA method, " +
            "and is sent back to `return_uri`.",
        tags = ["client"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Where to send the end-user's browser to enroll an MFA method.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "- `client.mfa.enrollment.mfa_disabled`: `mfa.totp.enabled` is not `true` in the " +
                    "configuration.\n" +
                    "- `client.mfa.enrollment.invalid_access_token`: `access_token` is absent, is not an active " +
                    "access token this server issued to this client, or was issued for no end-user.\n" +
                    RETURN_URI_BAD_REQUEST_DESCRIPTION + "\n" +
                    BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = CLIENT_TOKEN_UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `users:mfa:write`."
            ),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ]
    )
    @Post
    @Secured(CLIENT_USERS_MFA_WRITE)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.USERS_MFA_WRITE])
    suspend fun startEnrollment(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @Body resource: ClientMfaEnrollmentInputResource
    ): ClientMfaEnrollmentResource {
        // Fail fast (before creating any session) when MFA is not enabled on this server.
        if ((uncheckedMfaConfig as? EnabledMfaConfig)?.enabled != true) {
            throw recoverableBusinessExceptionOf(
                "client.mfa.enrollment.mfa_disabled",
                "description.client.mfa.enrollment.mfa_disabled"
            )
        }

        val client = authentication.clientAuthentication.client

        // Validate the end-user access token: signature, expiry, grant not revoked, issued to the calling client
        // (enforced by introspectToken), and associated with an end-user (not a client-credentials token).
        val userToken = resource.accessToken
            ?.let { tokenManager.introspectToken(client, it, ACCESS) }
        // A token whose account is gone names no end-user any more, which is the same refusal.
        val user = userToken?.grant?.userId?.let { userManager.findByIdOrNull(it) }
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
            user = user,
            returnUri = returnUri,
            flow = flow,
            initiatingClientId = client.id,
            cancelUri = cancelUri
        )
        interactiveAuthFlowSessionControllerUtil.observeStartedSession(session, observedRequest)

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
