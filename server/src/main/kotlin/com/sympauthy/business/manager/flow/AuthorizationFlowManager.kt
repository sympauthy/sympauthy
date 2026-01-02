package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import jakarta.inject.Singleton

/**
 * Manager providing methods shared between all types of authorization flows.
 */
@Singleton
class AuthorizationFlowManager {

    /**
     * Return true if the authorization flow can issue a token for the given [authorizeAttempt].
     *
     * FIXME: to use in TokenController
     */
    suspend fun canIssueToken(authorizeAttempt: AuthorizeAttempt): Boolean {
        return false
    }

    /**
     * Complete the authorization flow for the given [authorizeAttempt].
     */
    suspend fun completeAuthorization() {
    }
}
