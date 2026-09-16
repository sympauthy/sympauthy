package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.business.model.flow.mfaPassedDateOrNull
import com.sympauthy.business.model.flow.userIdOrNull
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * [InteractiveFlowPurposeHandler] for [InteractiveFlowPurpose.MFA_ENROLLMENT].
 *
 * Drives the end-user through enrolling an MFA method. The purpose resolves once the end-user has passed the
 * MFA step for the session — set when the enrollment is confirmed (which doubles as passing the challenge)
 * or, when enrollment is optional, when it is skipped.
 *
 * While not resolved, it presents the enrollment method-selection step
 * ([InteractiveFlowStep.MfaSelectionForEnrollment]); that page lets the end-user choose the method to enroll
 * (and skip when optional), auto-redirecting to the method's enrollment step when there is a single method and
 * no skip.
 *
 * The purpose has no terminal effect: passing MFA is recorded during its step, so the engine simply marks it
 * complete once resolved.
 */
@Singleton
class MfaEnrollmentInteractiveFlowPurposeHandler(
    @Inject private val totpManager: TotpManager,
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.MFA_ENROLLMENT

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        return if (session.mfaPassed) null else InteractiveFlowStep.MfaSelectionForEnrollment
    }

    /**
     * Which methods the account has ended up with, and when this session resolved the enrollment — which is
     * the pair that says whether it enrolled one here or arrived with it.
     *
     * The enrollments are read for their **type only**. The seed behind a TOTP enrollment and the recovery
     * codes beside it are never emitted: one published here is a second factor defeated for good, and undoing
     * it means re-enrolling the person.
     *
     * A session with no user has no enrollments to read, and answers with nothing. An account that has
     * enrolled none answers `none` instead: those are different facts, and this purpose stalls on the second
     * of them.
     */
    override suspend fun debugInformation(session: InteractiveFlowSession): List<PurposeDebugInformation> {
        return listOf(
            PurposeDebugInformation("Enrolled methods", enrolledMethodsOf(session)),
            PurposeDebugInformation("MFA passed date", session.mfaPassedDateOrNull?.toString())
        )
    }

    private suspend fun enrolledMethodsOf(session: InteractiveFlowSession): String? {
        val userId = session.userIdOrNull ?: return null
        return if (totpManager.findConfirmedEnrollments(userId).isNotEmpty()) "totp" else "none"
    }
}
