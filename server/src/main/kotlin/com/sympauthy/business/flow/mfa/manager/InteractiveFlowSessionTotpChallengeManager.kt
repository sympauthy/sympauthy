package com.sympauthy.business.flow.mfa.manager

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.flow.manager.InteractiveFlowSessionManager
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.mfa.manager.TotpManager
import com.sympauthy.business.user.model.User
import com.sympauthy.exception.toOutcome
import com.sympauthy.platform.monitoring.telemetry.manager.AuthenticationMetrics
import com.sympauthy.platform.monitoring.telemetry.manager.AuthenticationMetrics.Companion.SECOND_FACTOR
import com.sympauthy.platform.monitoring.telemetry.manager.AuthenticationMetrics.Companion.TOTP
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class InteractiveFlowSessionTotpChallengeManager(
    @Inject private val totpManager: TotpManager,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val authenticationMetrics: AuthenticationMetrics
) {

    /**
     * Validates the TOTP [code] submitted by [user] during the MFA step of the authorization flow.
     *
     * Throws a recoverable [com.sympauthy.business.exception.BusinessException] if:
     * - the [code] is null or blank.
     * - the [code] does not match any of the user's confirmed TOTP enrollments.
     *
     * On success, records [com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession.mfaPassedDate]
     * on the session and returns the updated session.
     */
    suspend fun validateTotpChallenge(
        session: OnGoingInteractiveFlowSession,
        user: User,
        code: String?
    ): InteractiveFlowSession = authenticationMetrics.recordAttempt(TOTP, null, SECOND_FACTOR, Throwable::toOutcome) {
        if (code.isNullOrBlank() || !totpManager.isCodeValidForUser(user, code)) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.mfa.totp.challenge.invalid_code",
                descriptionId = "description.flow.mfa.totp.challenge.invalid_code"
            )
        }
        return@recordAttempt sessionManager.setMfaPassed(session)
    }
}
