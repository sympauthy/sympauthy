package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.manager.auth.oauth2.AuthorizeManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
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
    @Inject private val authorizeManager: AuthorizeManager,
    @Inject private val authorizationCodeManager: AuthorizationCodeManager
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
     * Return the [URI] where the end-user must be redirected to according to the [result].
     */
    suspend fun getRedirectUri(
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow,
        result: AuthorizationFlowResult
    ): URI {
        return when {
            result.missingRequiredClaims -> getRedirectUriToCollectClaims(
                authorizeAttempt = authorizeAttempt,
                flow = flow
            )

            result.missingValidation -> getRedirectUriToValidateCode(
                authorizeAttempt = authorizeAttempt,
                flow = flow
            )

            result.complete -> getRedirectUriToClient(
                authorizeAttempt = authorizeAttempt
            )

            else -> TODO()
        }
    }

    /**
     * Return the [URI] where the end-user must be redirected when there is an error during the authorization flow.
     */
    fun getErrorUri(
        flow: WebAuthorizationFlow,
        errorCode: String?,
        details: String? = null,
        description: String? = null
    ): URI {
        return flow.errorUri.let(UriBuilder::of)
            .apply {
                errorCode?.let { queryParam("error_code", it) }
                description?.let { queryParam("description", it) }
                details?.let { queryParam("details", it) }
            }
            .build()
    }

    /**
     * Return a [URI] where the end-user must be redirected to collect claims from him.
     */
    private suspend fun getRedirectUriToCollectClaims(
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow
    ): URI {
        return appendStateToUri(
            authorizeAttempt = authorizeAttempt,
            uri = flow.collectClaimsUri
        )
    }

    /**
     * Return a [URI] where the end-user must be redirected to collect claims from him.
     */
    private suspend fun getRedirectUriToValidateCode(
        authorizeAttempt: AuthorizeAttempt,
        flow: WebAuthorizationFlow
    ): URI {
        return appendStateToUri(
            authorizeAttempt = authorizeAttempt,
            uri = flow.validateCodeUri
        )
    }

    /**
     * Return a [URI] redirecting the end-user to the client with an authorization code.
     * The authorization code may be exchanged for tokens by the client using the token endpoint.
     */
    private suspend fun getRedirectUriToClient(
        authorizeAttempt: AuthorizeAttempt
    ): URI {
        val builder = UriBuilder.of(authorizeAttempt.redirectUri)
        authorizeAttempt.state
            ?.let { builder.queryParam("state", it) }
        authorizationCodeManager.generateCode(authorizeAttempt)
            .let { builder.queryParam("code", it.code) }
        return builder.build()
    }

    private suspend fun appendStateToUri(
        authorizeAttempt: AuthorizeAttempt,
        uri: URI
    ): URI {
        val state = authorizeManager.encodeState(authorizeAttempt)
        return uri.let(UriBuilder::of)
            .queryParam("state", state)
            .build()
    }
}
