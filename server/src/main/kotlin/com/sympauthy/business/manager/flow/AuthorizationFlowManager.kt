package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.ScopeGrantingManager
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager providing methods shared between all types of authorization flows.
 */
@Singleton
class AuthorizationFlowManager(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val scopeGrantingManager: ScopeGrantingManager
) {

    /**
     * Return true if the authorization flow can issue a token for the given [authorizeAttempt].
     *
     * FIXME: to use in TokenController
     */
    suspend fun canIssueToken(authorizeAttempt: AuthorizeAttempt): Boolean {
        return false
    }

    /**
     * Complete the authorization flow for the given [authorizeAttempt] and return the completed [authorizeAttempt].
     */
    suspend fun completeAuthorization(
        authorizeAttempt: AuthorizeAttempt
    ): AuthorizeAttempt {
        if (authorizeAttempt.complete) {
            return authorizeAttempt
        }
        var modifiedAuthorizedAttempt = authorizeAttempt

        // FIXME: Verify that the attempt is completable (has a user ?, more ?)

        val grantScopesResult = scopeGrantingManager.grantScopes(authorizeAttempt)
        modifiedAuthorizedAttempt = authorizeAttemptManager.setGrantedScopes(
            authorizeAttempt = modifiedAuthorizedAttempt,
            grantedScopes = grantScopesResult.grantedScopes
        )

        return authorizeAttemptManager.markAsComplete(modifiedAuthorizedAttempt)
    }
}
