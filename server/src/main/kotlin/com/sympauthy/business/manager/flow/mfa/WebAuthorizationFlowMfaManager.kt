package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.user.User
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class WebAuthorizationFlowMfaManager(
    @Inject private val totpManager: TotpManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder
) {

    /**
     * Returns the [URI] where the end-user must be redirected for the MFA step.
     *
     * Routes to the TOTP challenge page if the [user] has at least one confirmed TOTP enrollment,
     * or to the TOTP enrollment page otherwise.
     */
    suspend fun getMfaRedirectUri(
        authorizeAttempt: AuthorizeAttempt,
        user: User,
        flow: WebAuthorizationFlow
    ): URI {
        val hasEnrollment = totpManager.findConfirmedEnrollments(user.id).isNotEmpty()
        return redirectUriBuilder.getMfaRedirectUri(authorizeAttempt, flow, hasEnrollment)
    }
}
