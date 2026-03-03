package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.User
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class WebAuthorizationFlowTotpChallengeManager(
    @Inject private val totpManager: TotpManager
) {

    /**
     * Validates the TOTP [code] submitted by [user] during the MFA step of the authorization flow.
     *
     * Throws a recoverable [com.sympauthy.business.exception.BusinessException] if:
     * - the [code] is null or blank.
     * - the [code] does not match any of the user's confirmed TOTP enrollments.
     *
     * Returns the [authorizeAttempt] unchanged on success.
     */
    suspend fun validateTotpChallenge(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        user: User,
        code: String?
    ): AuthorizeAttempt {
        if (code.isNullOrBlank() || !totpManager.isCodeValidForUser(user.id, code)) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.mfa.totp.challenge.invalid_code",
                descriptionId = "description.flow.mfa.totp.challenge.invalid_code"
            )
        }
        return authorizeAttempt
    }
}
