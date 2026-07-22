package com.sympauthy.business.manager.flow.reauth

import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.reauth.PassedReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationPurpose
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Runs the consumer-specific action once a [PassedReAuthenticationAttempt] has been obtained, keeping the generic
 * sign-in managers (password / provider) decoupled from any particular re-authentication [ReAuthenticationPurpose].
 */
@Singleton
class ReAuthenticationCompletionDispatcher(
    @Inject private val providerAttachManager: WebAuthorizationFlowProviderAttachManager
) {

    suspend fun complete(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        reAuthentication: PassedReAuthenticationAttempt
    ): AuthorizeAttempt {
        return when (reAuthentication.purpose) {
            ReAuthenticationPurpose.PROVIDER_ATTACH ->
                providerAttachManager.completeAttach(authorizeAttempt, reAuthentication)
        }
    }
}
