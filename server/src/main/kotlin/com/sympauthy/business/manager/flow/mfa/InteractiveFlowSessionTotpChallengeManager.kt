package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.User
import com.sympauthy.exception.toOutcome
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics.Companion.SECOND_FACTOR
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics.Companion.TOTP
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
     * On success, records [com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession.mfaPassedDate]
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
