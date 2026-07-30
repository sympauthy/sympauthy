package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
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
class MfaEnrollmentInteractiveFlowPurposeHandler : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.MFA_ENROLLMENT

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        return if (session.mfaPassed) null else InteractiveFlowStep.MfaSelectionForEnrollment
    }
}
