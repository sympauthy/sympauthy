package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.user.User
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager owning the MFA **challenge** purpose. It routes the challenge method-selection step: auto-redirect
 * to the challenge when a single method is enrolled, or offer a choice when several methods are enrolled.
 *
 * The enrollment purpose is owned by [InteractiveFlowSessionMfaEnrollmentManager]; the two are kept isolated.
 */
@Singleton
class InteractiveFlowSessionMfaChallengeManager(
    @Inject private val totpManager: TotpManager
) {

    /**
     * Return the [MfaRoutingResult] for the challenge method-selection step of the [user].
     *
     * Auto-redirects to the challenge when a single method is enrolled; offers a choice (no skip) when the
     * user has enrolled several methods.
     */
    suspend fun getChallengeRoutingResult(
        user: User
    ): MfaRoutingResult {
        val enrollments = totpManager.findConfirmedEnrollments(user.id)
        return if (enrollments.size > 1) {
            MfaMethodSelection(
                methods = listOf(
                    AvailableMfaMethod(name = "TOTP", step = InteractiveFlowStep.MfaTotpChallenge)
                ),
                skippable = false
            )
        } else {
            MfaAutoRedirect(InteractiveFlowStep.MfaTotpChallenge)
        }
    }
}
