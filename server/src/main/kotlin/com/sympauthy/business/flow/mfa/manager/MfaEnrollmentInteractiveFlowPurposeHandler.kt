package com.sympauthy.business.flow.mfa.manager

import com.sympauthy.business.flow.manager.InteractiveFlowPurposeHandler
import com.sympauthy.business.flow.model.InteractiveFlowPurpose
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.flow.model.InteractiveFlowStep
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.flow.model.PurposeDebugInformation
import com.sympauthy.business.flow.model.mfaPassedDateOrNull
import com.sympauthy.business.flow.model.userIdOrNull
import com.sympauthy.business.mfa.manager.TotpManager
import com.sympauthy.platform.util.wireName
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
     * Only the **types** of the account's methods are read, so the seed behind a TOTP enrollment and the
     * recovery codes beside it never reach here: one published here is a second factor defeated for good, and
     * undoing it means re-enrolling the person.
     *
     * A session with no user has no enrollments to read, and answers with nothing. An account that has
     * enrolled none answers an empty list instead: those are different facts, and this purpose stalls on the
     * second of them.
     */
    override suspend fun debugInformation(session: InteractiveFlowSession): List<PurposeDebugInformation> {
        val enrolled = session.userIdOrNull?.let { userId ->
            totpManager.findEnrolledMethodTypes(userId).map { it.wireName }
        }
        return listOf(
            PurposeDebugInformation.TextList("enrolled_methods", enrolled),
            PurposeDebugInformation.Date("mfa_passed_at", session.mfaPassedDateOrNull)
        )
    }
}
