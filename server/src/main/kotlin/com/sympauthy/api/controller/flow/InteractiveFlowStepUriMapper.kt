package com.sympauthy.api.controller.flow

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.CANCELLED_DETAILS_ID
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowHandBack
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus.CANCELLED
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus.COMPLETED
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus.EXPIRED
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus.FAILED
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus.ONGOING
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.ResetPasswordPages
import com.sympauthy.business.model.flow.status
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.ACCESS_DENIED
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.wireName
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

/**
 * Web transport of the interactive flow: maps an abstract [InteractiveFlowStep] produced by the flow's purpose
 * handler to the concrete [URI] where the end-user must be redirected, using the flow's configured page
 * URIs ([InteractiveFlow]).
 *
 * This is the only place that turns steps into URIs; a different transport (e.g. a native client not relying
 * on URLs) would provide its own mapper.
 */
@Singleton
class InteractiveFlowStepUriMapper(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val authorizationCodeManager: AuthorizationCodeManager,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    /**
     * Map the abstract [step] to the concrete [URI] where the end-user must be redirected, using the
     * [flow]'s configured page URIs.
     */
    suspend fun toRedirectUri(
        session: InteractiveFlowSession,
        flow: InteractiveFlow,
        step: InteractiveFlowStep
    ): URI = when (step) {
        InteractiveFlowStep.SignIn -> appendState(session, flow.signInUri)
        // Fall back to the sign-in page when the flow configures no sign-up page (web transport concern).
        InteractiveFlowStep.SignUp -> appendState(session, flow.signUpUri ?: flow.signInUri)
        InteractiveFlowStep.MfaSelectionForEnrollment -> appendState(
            session,
            flow.mfaSelectionForEnrollmentUri
                ?: throw internalBusinessExceptionOf("flow.mfa.selection_for_enrollment_uri.missing")
        )
        InteractiveFlowStep.MfaSelectionForChallenge -> appendState(
            session,
            flow.mfaSelectionForChallengeUri
                ?: throw internalBusinessExceptionOf("flow.mfa.selection_for_challenge_uri.missing")
        )
        InteractiveFlowStep.MfaTotpEnroll -> appendState(
            session,
            flow.mfaTotpEnrollUri ?: throw internalBusinessExceptionOf("flow.mfa.totp.enroll_uri.missing")
        )
        InteractiveFlowStep.MfaTotpChallenge -> appendState(
            session,
            flow.mfaTotpChallengeUri ?: throw internalBusinessExceptionOf("flow.mfa.totp.challenge_uri.missing")
        )
        InteractiveFlowStep.Confirm -> appendState(
            session,
            flow.confirmUri ?: throw internalBusinessExceptionOf("flow.confirm.uri.missing")
        )
        // Drive the browser to the target provider's authorize endpoint (which then 303s to the provider),
        // reusing the existing provider authorize/callback machinery. No configurable flow page is involved.
        is InteractiveFlowStep.AuthorizeProvider -> appendState(
            session,
            uncheckedUrlsConfig.orThrow().getUri(
                ProvidersController.FLOW_PROVIDER_ENDPOINTS + ProvidersController.FLOW_PROVIDER_AUTHORIZE_ENDPOINT,
                "providerId" to step.providerId
            )
        )
        InteractiveFlowStep.ResetPasswordValidate -> appendState(session, getResetPasswordPages(flow).validateUri)
        InteractiveFlowStep.ResetPasswordChoose -> appendState(session, getResetPasswordPages(flow).chooseUri)
        InteractiveFlowStep.CollectClaims -> appendState(session, flow.collectClaimsUri)
        is InteractiveFlowStep.ValidateClaims -> appendState(session, buildValidateClaimsUri(flow, step.media))
        InteractiveFlowStep.Error, InteractiveFlowStep.AlreadyCompleted -> appendState(session, flow.errorUri)
        InteractiveFlowStep.Complete -> buildCompletionRedirectUri(
            session as? CompletedInteractiveFlowSession
                ?: throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
        )
        InteractiveFlowStep.Cancel -> getLeaveUriOrNull(
            session as? CancelledInteractiveFlowSession
                ?: throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
        ) ?: throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
    }

    /**
     * Route a completed [session] to the URI where the end-user must be handed back, based on the session's
     * [InteractiveFlowSession.handBack] — handing the client the authorization code the session completed with
     * for an [InteractiveFlowRedirectType.AUTHORIZATION_CODE] session, or returning to the return URI naming the
     * flow for an [InteractiveFlowRedirectType.PLAIN] session.
     */
    private suspend fun buildCompletionRedirectUri(session: CompletedInteractiveFlowSession): URI {
        val handBack = session.handBack
        return when (handBack.type) {
            InteractiveFlowRedirectType.AUTHORIZATION_CODE -> buildClientRedirectUri(session, handBack)
            InteractiveFlowRedirectType.PLAIN -> buildReturnUri(session, handBack, flowError = null)
        }
    }

    /**
     * Return the `cancel_url` of a step drawn for [session]: the URL of the endpoint letting the end-user leave
     * the flow, carrying [state], the state the request resolved [session] from, as it was received, or a newly
     * signed one where it is null. Null where leaving has nowhere to send them — the session has no
     * [InteractiveFlowSession.handBack], or it is an authorization that has completed, whose code went to the
     * client and to which OAuth2 defines no second answer, whether or not it has since expired.
     */
    suspend fun getCancelUriOrNull(session: InteractiveFlowSession, state: String?): URI? {
        val handBack = session.handBack ?: return null
        val authorization = handBack.type == InteractiveFlowRedirectType.AUTHORIZATION_CODE
        if (authorization && session.status == InteractiveFlowSessionStatus.COMPLETED) return null
        val uri = uncheckedUrlsConfig.orThrow().getUri(CancelController.FLOW_CANCEL_ENDPOINT)
        return if (state != null) appendState(state, uri) else appendState(session, uri)
    }

    /**
     * Return the [URI] the end-user is sent to on leaving the [session] where it now stands, or null where
     * leaving it sends them nowhere: the session is still running, it has no [InteractiveFlowSession.handBack],
     * or it is an authorization that has completed.
     *
     * The session is answered by what ended it ([status]): one that completed or was cancelled before its
     * expiration passed is answered as such, never as expired.
     *
     * - An [InteractiveFlowRedirectType.AUTHORIZATION_CODE] session answers through the protocol, on the client's
     *   `redirect_uri`, the client's `state` echoed and no code: the error the authorize endpoint recorded where it
     *   refused the request itself, and `access_denied` where the end-user cancelled it or it failed or expired
     *   once they were going through the flow.
     * - An [InteractiveFlowRedirectType.PLAIN] session returns to its return URI naming the flow, and how it ended
     *   when it did not complete: [CANCELLED_DETAILS_ID] where the end-user cancelled it, the code it failed with
     *   where it failed or expired.
     */
    suspend fun getLeaveUriOrNull(session: InteractiveFlowSession): URI? {
        val handBack = session.handBack ?: return null
        val status = session.status
        return when (handBack.type) {
            InteractiveFlowRedirectType.AUTHORIZATION_CODE -> when (status) {
                ONGOING, COMPLETED -> null
                CANCELLED, EXPIRED -> buildClientErrorUri(session, handBack) { ACCESS_DENIED }
                FAILED -> buildClientErrorUri(session, handBack) { it.requestError ?: ACCESS_DENIED }
            }

            InteractiveFlowRedirectType.PLAIN -> when (status) {
                ONGOING -> null
                COMPLETED -> buildReturnUri(session, handBack, flowError = null)
                CANCELLED -> buildReturnUri(session, handBack, CANCELLED_DETAILS_ID)
                FAILED, EXPIRED -> buildReturnUri(
                    session, handBack, (session as? FailedInteractiveFlowSession)?.errorDetailsId
                )
            }
        }
    }

    /**
     * Return the return URI of [handBack] for [session], carrying [FLOW_PARAMETER] set to the purpose that
     * started it, [FLOW_ERROR_PARAMETER] set to [flowError] where it did not complete, and
     * [RETURN_STATE_PARAMETER] set to the `return_state` its start request carried where it carried one.
     */
    private fun buildReturnUri(
        session: InteractiveFlowSession,
        handBack: InteractiveFlowHandBack,
        flowError: String?
    ): URI {
        val builder = UriBuilder.of(handBack.uri)
            .queryParam(FLOW_PARAMETER, session.initiatingPurpose.wireName)
        flowError?.let { builder.queryParam(FLOW_ERROR_PARAMETER, it) }
        handBack.returnState?.let { builder.queryParam(RETURN_STATE_PARAMETER, it) }
        return builder.build()
    }

    /**
     * Return the client's `redirect_uri` of [handBack] carrying the OAuth2 error [error] picks from the session's
     * OAuth2 record, with the client's own `state` echoed where its request carried one, and no code.
     */
    private suspend fun buildClientErrorUri(
        session: InteractiveFlowSession,
        handBack: InteractiveFlowHandBack,
        error: (InteractiveFlowSessionOAuth2) -> OAuth2ErrorCode
    ): URI {
        val oauth2 = oauth2Manager.fetchOAuth2(session)
        val builder = UriBuilder.of(handBack.uri)
            .queryParam("error", error(oauth2).errorCode)
        oauth2.state?.let { builder.queryParam("state", it) }
        return builder.build()
    }

    /**
     * Return the [URI] where the end-user must be redirected to start the authorization flow (sign-in page).
     */
    suspend fun getSignInRedirectUri(session: InteractiveFlowSession, flow: InteractiveFlow): URI =
        appendState(session, flow.signInUri)

    /**
     * Return the [URI] of the flow's sign-up page, or null if the flow does not configure one.
     */
    suspend fun getSignUpRedirectUri(session: InteractiveFlowSession, flow: InteractiveFlow): URI? =
        flow.signUpUri?.let { appendState(session, it) }

    /**
     * Return the [URI] of the flow's page where the end-user asks to reset their password, or null if the flow
     * names no reset pages.
     */
    suspend fun getResetPasswordRedirectUriOrNull(session: InteractiveFlowSession, flow: InteractiveFlow): URI? =
        flow.resetPassword?.let { appendState(session, it.resetPasswordUri) }

    private fun getResetPasswordPages(flow: InteractiveFlow): ResetPasswordPages =
        flow.resetPassword ?: throw internalBusinessExceptionOf("flow.password.reset.disabled")

    /**
     * Return the [URI] where the end-user must be redirected when there is an error during the flow.
     */
    suspend fun getErrorUri(session: InteractiveFlowSession, flow: InteractiveFlow): URI =
        appendState(session, flow.errorUri)

    /**
     * Return the [URI] of the error page of [flow] carrying [state] as it was received, for a state that
     * resolves to no session: the error page then answers the refusal of that state itself.
     */
    fun getErrorUri(state: String, flow: InteractiveFlow): URI = appendState(state, flow.errorUri)

    /**
     * Return the [URI] of the MFA skip API endpoint, with the state appended.
     * Used as the `skip_redirect_url` in the MFA selection screen response.
     */
    suspend fun getMfaSkipUri(session: InteractiveFlowSession, skipEndpointPath: String): URI {
        val uri = uncheckedUrlsConfig.orThrow().getUri(skipEndpointPath)
        return appendState(session, uri)
    }

    private fun buildValidateClaimsUri(flow: InteractiveFlow, media: ValidationCodeMedia?): URI {
        return flow.validateClaimsUri.let(UriBuilder::of)
            .apply { media?.let { queryParam("media", it.wireName) } }
            .build()
    }

    /**
     * Return a [URI] redirecting the end-user to the client with the authorization code [session] completed
     * with. The code may be exchanged for tokens by the client using the token endpoint.
     *
     * The base URI is the session's hand-back URI (== the client `redirect_uri`); the OAuth2 record is
     * read only to echo the client's own `state`. Note the internal state is intentionally NOT appended here:
     * only the client's own `state` is echoed back, as required by OAuth2.
     *
     * **The code is read back, although the request building this redirect is the one that just wrote it.**
     * It is written by the completion, inside its transaction, and nothing carries it out: an applied terminal
     * effect answers nothing, and putting the code on the session or the step would hand a credential to every
     * caller of the engine. Writing it here instead is what let a repeated request mint
     * a second one. One read per authorization is the accepted price.
     */
    private suspend fun buildClientRedirectUri(
        session: CompletedInteractiveFlowSession,
        handBack: InteractiveFlowHandBack
    ): URI {
        val builder = UriBuilder.of(handBack.uri)
        oauth2Manager.fetchOAuth2(session).state
            ?.let { builder.queryParam("state", it) }
        authorizationCodeManager.fetchCode(session)
            .let { builder.queryParam("code", it.code) }
        return builder.build()
    }

    /**
     * Append the signed internal state identifying the [session] to [uri] as the `state` query parameter.
     * Public because it appends the state to any URI, not only to a step's own — a third-party provider's
     * authorize URL among them.
     */
    suspend fun appendState(session: InteractiveFlowSession, uri: URI): URI =
        appendState(sessionManager.encodeState(session), uri)

    /**
     * Append the already signed [state] to [uri] as the `state` query parameter.
     */
    private fun appendState(state: String, uri: URI): URI =
        uri.let(UriBuilder::of)
            .queryParam("state", state)
            .build()

    companion object {
        /**
         * The parameter a return URI carries naming the flow the end-user is returning from: the purpose its session
         * was started for, as the admin API publishes it.
         */
        const val FLOW_PARAMETER = "sympauthy_flow"

        /**
         * The parameter a return URI carries where the flow did not complete, naming how it ended.
         */
        const val FLOW_ERROR_PARAMETER = "sympauthy_flow_error"

        /**
         * The parameter a return URI carries the `return_state` of the start request back in.
         */
        const val RETURN_STATE_PARAMETER = "sympauthy_return_state"
    }
}
