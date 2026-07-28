package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowStatus
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

/**
 * Component in charge of constructing the URIs where the end-user will be redirected to continue its
 * authentication & authorization through a web authorization flow.
 */
@Singleton
class InteractiveAuthFlowSessionRedirectUriBuilder(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val authorizationCodeManager: AuthorizationCodeManager,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    /**
     * Return the [URI] where the end-user must be redirected to start the authorization flow.
     */
    suspend fun getSignInRedirectUri(
        session: InteractiveFlowSession,
        flow: InteractiveFlow
    ): URI {
        return appendStateToUri(
            session = session,
            uri = flow.signInUri
        )
    }

    /**
     * Return the [URI] where the end-user must be redirected to reach the sign-up page of the flow,
     * or null if the flow does not configure a sign-up page.
     */
    suspend fun getSignUpRedirectUri(
        session: InteractiveFlowSession,
        flow: InteractiveFlow
    ): URI? {
        val uri = flow.signUpUri ?: return null
        return appendStateToUri(
            session = session,
            uri = uri
        )
    }

    /**
     * Return the [URI] where the end-user must be redirected to according to the [status].
     */
    suspend fun getRedirectUri(
        session: InteractiveFlowSession,
        flow: InteractiveFlow,
        status: InteractiveFlowStatus
    ): URI = when (session) {
        is CompletedInteractiveFlowSession -> getRedirectUriToClient(session)
        is FailedInteractiveFlowSession -> getErrorUri(session, flow)
        is OnGoingInteractiveFlowSession -> {
            when {
                status.missingUser -> {
                    val invitationId = oauth2Manager.fetchOAuth2(session).invitationId
                    appendStateToUri(
                        session = session,
                        uri = if (invitationId != null && flow.signUpUri != null) {
                            flow.signUpUri
                        } else {
                            flow.signInUri
                        }
                    )
                }

                status.missingMfa -> appendStateToUri(
                    session = session,
                    uri = flow.mfaUri ?: throw internalBusinessExceptionOf("flow.mfa.uri.missing")
                )

                status.missingRequiredClaims -> appendStateToUri(
                    session = session,
                    uri = flow.collectClaimsUri
                )

                status.missingMediaForClaimValidation.isNotEmpty() -> getRedirectUriToClaimValidation(
                    session = session,
                    flow = flow,
                    result = status,
                )

                else -> throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
            }
        }
    }

    /**
     * Return the [URI] where the end-user must be redirected when there is an error during the authorization flow.
     */
    suspend fun getErrorUri(
        session: InteractiveFlowSession,
        flow: InteractiveFlow
    ): URI {
        return appendStateToUri(
            session = session,
            uri = flow.errorUri
        )
    }

    /**
     * Return the [URI] where the end-user must be redirected to validate
     */
    suspend fun getRedirectUriToClaimValidation(
        session: InteractiveFlowSession,
        flow: InteractiveFlow,
        result: InteractiveFlowStatus,
    ): URI {
        val uri = flow.validateClaimsUri.let(UriBuilder::of)
            .apply {
                result.missingMediaForClaimValidation.firstOrNull()?.let { queryParam("media", it.name) }
            }
            .build()
        return appendStateToUri(
            session = session,
            uri = uri,
        )
    }

    /**
     * Return a [URI] redirecting the end-user to the client with an authorization code.
     * The authorization code may be exchanged for tokens by the client using the token endpoint.
     */
    internal suspend fun getRedirectUriToClient(
        session: CompletedInteractiveFlowSession
    ): URI {
        val oauth2 = oauth2Manager.fetchOAuth2(session)
        val builder = UriBuilder.of(oauth2.redirectUri)
        oauth2.state
            ?.let { builder.queryParam("state", it) }
        authorizationCodeManager.generateCode(session)
            .let { builder.queryParam("code", it.code) }
        return builder.build()
    }

    /**
     * Return the [URI] where the end-user must be redirected to enroll a TOTP authenticator.
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if the URI is not configured.
     */
    suspend fun getMfaTotpEnrollUri(
        session: InteractiveFlowSession,
        flow: InteractiveFlow
    ): URI {
        val uri = flow.mfaTotpEnrollUri ?: throw internalBusinessExceptionOf("flow.mfa.totp.enroll_uri.missing")
        return appendStateToUri(session, uri)
    }

    /**
     * Return the [URI] where the end-user must be redirected to complete the TOTP challenge.
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if the URI is not configured.
     */
    suspend fun getMfaTotpChallengeUri(
        session: InteractiveFlowSession,
        flow: InteractiveFlow
    ): URI {
        val uri = flow.mfaTotpChallengeUri ?: throw internalBusinessExceptionOf("flow.mfa.totp.challenge_uri.missing")
        return appendStateToUri(session, uri)
    }

    /**
     * Return the [URI] of the MFA skip API endpoint, with the state appended.
     * Used as the `skip_redirect_url` in the MFA selection screen response.
     */
    suspend fun getMfaSkipUri(
        session: InteractiveFlowSession,
        skipEndpointPath: String
    ): URI {
        val uri = uncheckedUrlsConfig.orThrow().getUri(skipEndpointPath)
        return appendStateToUri(session, uri)
    }

    internal suspend fun appendStateToUri(
        session: InteractiveFlowSession,
        uri: URI
    ): URI {
        val state = sessionManager.encodeState(session)
        return uri.let(UriBuilder::of)
            .queryParam("state", state)
            .build()
    }
}
