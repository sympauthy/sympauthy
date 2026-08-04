package com.sympauthy.api.controller.flow

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

/**
 * Web transport of the interactive flow: maps an abstract [InteractiveFlowStep] produced by the flow's purpose
 * handler to the concrete [URI] where the end-user must be redirected, using the flow's configured page
 * URIs ([InteractiveFlow]).
 *
 * This is the only place that turns steps into URIs and the only place an authorization code is minted; a
 * different transport (e.g. a native client not relying on URLs) would provide its own mapper.
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
        InteractiveFlowStep.CollectClaims -> appendState(session, flow.collectClaimsUri)
        is InteractiveFlowStep.ValidateClaims -> appendState(session, buildValidateClaimsUri(flow, step.media))
        InteractiveFlowStep.Error -> appendState(session, flow.errorUri)
        InteractiveFlowStep.Complete -> buildCompletionRedirectUri(
            session as? CompletedInteractiveFlowSession
                ?: throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
        )
        InteractiveFlowStep.Cancel -> buildCancelRedirectUri(
            session as? CancelledInteractiveFlowSession
                ?: throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
        )
    }

    /**
     * Route a completed [session] to the URI where the end-user must be handed back, based on the session's
     * [InteractiveFlowSession] redirect type — issuing an authorization code to the client for an
     * [InteractiveFlowRedirectType.AUTHORIZATION_CODE] session, or returning to the caller-provided URI
     * verbatim for an [InteractiveFlowRedirectType.PLAIN] session.
     */
    private suspend fun buildCompletionRedirectUri(session: CompletedInteractiveFlowSession): URI =
        when (session.redirectType) {
            InteractiveFlowRedirectType.AUTHORIZATION_CODE -> buildClientRedirectUri(session)
            InteractiveFlowRedirectType.PLAIN -> session.successRedirectUri
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
     * Return the [URI] where the end-user must be redirected when there is an error during the flow.
     */
    suspend fun getErrorUri(session: InteractiveFlowSession, flow: InteractiveFlow): URI =
        appendState(session, flow.errorUri)

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
            .apply { media?.let { queryParam("media", it.name) } }
            .build()
    }

    /**
     * Return a [URI] redirecting the end-user to the client with an authorization code. The code may be
     * exchanged for tokens by the client using the token endpoint.
     *
     * The base URI is the session's success redirect URI (== the client `redirect_uri`); the OAuth2 record is
     * read only to echo the client's own `state`. Note the internal state is intentionally NOT appended here:
     * only the client's own `state` is echoed back, as required by OAuth2.
     */
    private suspend fun buildClientRedirectUri(session: CompletedInteractiveFlowSession): URI {
        val builder = UriBuilder.of(session.successRedirectUri)
        oauth2Manager.fetchOAuth2(session).state
            ?.let { builder.queryParam("state", it) }
        authorizationCodeManager.generateCode(session)
            .let { builder.queryParam("code", it.code) }
        return builder.build()
    }

    /**
     * Route a cancelled [session] to the URI where the end-user must be handed back on cancellation, based on
     * the session's redirect type — returning the OAuth2 `error=access_denied` response to the client
     * `redirect_uri` (with the client's `state` echoed, and no authorization code minted) for an
     * [InteractiveFlowRedirectType.AUTHORIZATION_CODE] session, or redirecting to the caller-provided cancel
     * URI verbatim for an [InteractiveFlowRedirectType.PLAIN] session.
     */
    private suspend fun buildCancelRedirectUri(session: CancelledInteractiveFlowSession): URI {
        val cancelRedirectUri = session.cancelRedirectUri
            ?: throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
        return when (session.redirectType) {
            InteractiveFlowRedirectType.AUTHORIZATION_CODE -> {
                val builder = UriBuilder.of(cancelRedirectUri)
                    .queryParam("error", OAuth2ErrorCode.ACCESS_DENIED.errorCode)
                oauth2Manager.fetchOAuth2(session).state
                    ?.let { builder.queryParam("state", it) }
                builder.build()
            }
            InteractiveFlowRedirectType.PLAIN -> cancelRedirectUri
        }
    }

    /**
     * Append the signed internal state identifying the [session] to [uri] as the `state` query parameter.
     * Public because callers building non-step redirects (e.g. a third-party provider authorize URL) reuse it.
     */
    suspend fun appendState(session: InteractiveFlowSession, uri: URI): URI {
        val state = sessionManager.encodeState(session)
        return uri.let(UriBuilder::of)
            .queryParam("state", state)
            .build()
    }
}
