package com.sympauthy.api.util.flow

import com.sympauthy.api.util.orBadRequest
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.user.User
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Utility methods shared between all the controllers of the authentication flow.
 */
@Singleton
class FlowControllerHelper(
    @Inject private val userManager: UserManager
) {

    // FIXME: Migrate to [WebAuthorizationFlowManager]
    suspend fun getUser(authorizeAttempt: AuthorizeAttempt): User {
        val userId = authorizeAttempt.userId.orBadRequest("flow.user.missing")
        return userManager.findById(userId) ?: throw IllegalStateException("No user found with id $userId")
    }
}
