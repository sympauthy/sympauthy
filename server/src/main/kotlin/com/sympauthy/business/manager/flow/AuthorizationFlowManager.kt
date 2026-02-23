package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.ScopeGrantingManager
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.AuthorizationFlow.Companion.DEFAULT_WEB_AUTHORIZATION_FLOW_ID
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.view.DefaultAuthorizationFlowController.Companion.USER_FLOW_ENDPOINT
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager providing methods shared between all types of authorization flows.
 */
@Singleton
class AuthorizationFlowManager(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val scopeGrantingManager: ScopeGrantingManager,
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig,
    @Inject private val uncheckedFeaturesConfig: FeaturesConfig,
) {

    /**
     * Note: The default web authentication flow is hardcoded since it is bundled with this authorization server.
     */
    val defaultWebAuthorizationFlow: WebAuthorizationFlow by lazy {
        val builder = uncheckedUrlsConfig.orThrow().root
            .let(UriBuilder::of)
            .path(USER_FLOW_ENDPOINT)
        WebAuthorizationFlow(
            id = DEFAULT_WEB_AUTHORIZATION_FLOW_ID,
            signInUri = builder.path("sign-in").build(),
            collectClaimsUri = builder.path("claims/edit").build(),
            validateClaimsUri = builder.path("claims/validate").build(),
            errorUri = builder.path("error").build(),
        )
    }

    /**
     * Return the [AuthorizationFlow] identified by [id] or null.
     */
    fun findByIdOrNull(id: String): AuthorizationFlow? {
        if (id == DEFAULT_WEB_AUTHORIZATION_FLOW_ID) {
            return defaultWebAuthorizationFlow
        }
        return authorizationFlowsConfig.orThrow().flows
            .firstOrNull { it.id == id }
    }

    /**
     * Either return if the [authorizeAttempt] can be used to issue an access token or throws one of the following
     * exceptions:
     * - TODO
     */
    suspend fun checkCanIssueToken(authorizeAttempt: AuthorizeAttempt?): CompletedAuthorizeAttempt {
        // TODO: Implements validation
        if (authorizeAttempt !is CompletedAuthorizeAttempt) {
            throw businessExceptionOf("token.expired")
        }
        return authorizeAttempt
    }

    /**
     * Complete the authorization flow for the given [authorizeAttempt] and return the completed [authorizeAttempt].
     *
     * Is the allowAccessToClientWithoutScope flag is false: If no scope have been granted, then the [authorizeAttempt]
     * is marked as failed and the end-user is not allowed to continue to the client.
     *
     * The list of [allCollectedClaims] may be provided to prevent loading them again.
     */
    suspend fun completeAuthorization(
        authorizeAttempt: AuthorizeAttempt,
        allCollectedClaims: List<CollectedClaim>,
    ): AuthorizeAttempt {
        val featuresConfig = uncheckedFeaturesConfig.orThrow()

        if (authorizeAttempt !is OnGoingAuthorizeAttempt) {
            return authorizeAttempt
        }
        var modifiedAuthorizedAttempt = authorizeAttempt
        // FIXME: Verify that the attempt is completable (has a user ?, more ?)

        val grantScopesResult = scopeGrantingManager.grantScopes(
            authorizeAttempt = authorizeAttempt,
            allCollectedClaims = allCollectedClaims
        )
        modifiedAuthorizedAttempt = authorizeAttemptManager.setGrantedScopes(
            authorizeAttempt = modifiedAuthorizedAttempt,
            grantedScopes = grantScopesResult.grantedScopes
        )

        return if (modifiedAuthorizedAttempt.grantedScopes.isNullOrEmpty() && !featuresConfig.allowAccessToClientWithoutScope) {
            // Mark attempt as failed since no scope have been granted and the end-user is not allowed to continue
            // to the client in this state.
            authorizeAttemptManager.markAsFailedIfNotRecoverable(
                authorizeAttempt = authorizeAttempt,
                error = businessExceptionOf(
                    detailsId = "flow.authorization_flow.complete.no_scope"
                )
            )
        } else {
            authorizeAttemptManager.markAsComplete(modifiedAuthorizedAttempt)
        }
    }
}
