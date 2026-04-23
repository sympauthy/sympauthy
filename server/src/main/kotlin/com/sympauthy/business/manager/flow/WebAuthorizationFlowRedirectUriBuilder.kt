package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
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
class WebAuthorizationFlowRedirectUriBuilder(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val authorizationCodeManager: AuthorizationCodeManager,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    /**
     * Return the [URI] where the end-user must be redirected to start the authorization flow.
     */
    suspend fun getSignInRedirectUri(
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow
    ): URI {
        return appendStateToUri(
            authorizeAttempt = authorizeAttempt,
            uri = flow.signInUri
        )
    }

    /**
     * Return the [URI] where the end-user must be redirected to according to the [status].
     */
    suspend fun getRedirectUri(
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow,
        status: WebAuthorizationFlowStatus
    ): URI = when (authorizeAttempt) {
        is CompletedAuthorizeAttempt -> getRedirectUriToClient(authorizeAttempt)
        is FailedAuthorizeAttempt -> getErrorUri(authorizeAttempt, flow)
        is OnGoingAuthorizeAttempt -> {
            when {
                status.missingUser -> appendStateToUri(
                    authorizeAttempt = authorizeAttempt,
                    uri = flow.signInUri
                )

                status.missingMfa -> appendStateToUri(
                    authorizeAttempt = authorizeAttempt,
                    uri = flow.mfaUri ?: throw internalBusinessExceptionOf("flow.mfa.uri.missing")
                )

                status.missingRequiredClaims -> appendStateToUri(
                    authorizeAttempt = authorizeAttempt,
                    uri = flow.collectClaimsUri
                )

                status.missingMediaForClaimValidation.isNotEmpty() -> getRedirectUriToClaimValidation(
                    authorizeAttempt = authorizeAttempt,
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
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow
    ): URI {
        return appendStateToUri(
            authorizeAttempt = authorizeAttempt,
            uri = flow.errorUri
        )
    }

    /**
     * Return the [URI] where the end-user must be redirected to validate
     */
    suspend fun getRedirectUriToClaimValidation(
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow,
        result: WebAuthorizationFlowStatus,
    ): URI {
        val uri = flow.validateClaimsUri.let(UriBuilder::of)
            .apply {
                result.missingMediaForClaimValidation.firstOrNull()?.let { queryParam("media", it.name) }
            }
            .build()
        return appendStateToUri(
            authorizeAttempt = authorizeAttempt,
            uri = uri,
        )
    }

    /**
     * Return a [URI] redirecting the end-user to the client with an authorization code.
     * The authorization code may be exchanged for tokens by the client using the token endpoint.
     */
    internal suspend fun getRedirectUriToClient(
        authorizeAttempt: CompletedAuthorizeAttempt
    ): URI {
        val builder = UriBuilder.of(authorizeAttempt.redirectUri)
        authorizeAttempt.state
            ?.let { builder.queryParam("state", it) }
        authorizationCodeManager.generateCode(authorizeAttempt)
            .let { builder.queryParam("code", it.code) }
        return builder.build()
    }

    /**
     * Return the [URI] where the end-user must be redirected to enroll a TOTP authenticator.
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if the URI is not configured.
     */
    suspend fun getMfaTotpEnrollUri(
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow
    ): URI {
        val uri = flow.mfaTotpEnrollUri ?: throw internalBusinessExceptionOf("flow.mfa.totp.enroll_uri.missing")
        return appendStateToUri(authorizeAttempt, uri)
    }

    /**
     * Return the [URI] where the end-user must be redirected to complete the TOTP challenge.
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if the URI is not configured.
     */
    suspend fun getMfaTotpChallengeUri(
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow
    ): URI {
        val uri = flow.mfaTotpChallengeUri ?: throw internalBusinessExceptionOf("flow.mfa.totp.challenge_uri.missing")
        return appendStateToUri(authorizeAttempt, uri)
    }

    /**
     * Return the [URI] of the MFA skip API endpoint, with the state appended.
     * Used as the `skip_redirect_url` in the MFA selection screen response.
     */
    suspend fun getMfaSkipUri(
        authorizeAttempt: AuthorizeAttempt,
        skipEndpointPath: String
    ): URI {
        val uri = uncheckedUrlsConfig.orThrow().getUri(skipEndpointPath)
        return appendStateToUri(authorizeAttempt, uri)
    }

    internal suspend fun appendStateToUri(
        authorizeAttempt: AuthorizeAttempt,
        uri: URI
    ): URI {
        val state = authorizeAttemptManager.encodeState(authorizeAttempt)
        return uri.let(UriBuilder::of)
            .queryParam("state", state)
            .build()
    }
}
