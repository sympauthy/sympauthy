package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStepResult
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
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
 * When this is the session's initiating purpose (a standalone, client-initiated enrollment), completing it
 * marks the session complete; the caller is then handed back at the API boundary.
 */
@Singleton
class MfaEnrollmentInteractiveFlowPurposeHandler(
    @Inject private val sessionManager: InteractiveFlowSessionManager
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.MFA_ENROLLMENT

    override suspend fun getNextStep(session: OnGoingInteractiveFlowSession): InteractiveFlowPurposeStepResult {
        return if (session.mfaPassed) {
            InteractiveFlowPurposeStepResult.Resolved(session)
        } else {
            InteractiveFlowPurposeStepResult.Pending(session, InteractiveFlowStep.MfaSelectionForEnrollment)
        }
    }

    override suspend fun complete(session: OnGoingInteractiveFlowSession): InteractiveFlowSession {
        return sessionManager.markAsComplete(session)
    }
}
