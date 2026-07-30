package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import jakarta.inject.Singleton

/**
 * [InteractiveFlowPurposeHandler] for [InteractiveFlowPurpose.MFA_CHALLENGE].
 *
 * Drives the end-user through a challenge with an already-enrolled MFA method. The purpose resolves once the
 * end-user has passed the MFA step for the session — set when a valid TOTP code is submitted.
 *
 * While not resolved, it presents the challenge method-selection step
 * ([InteractiveFlowStep.MfaSelectionForChallenge]); that page lets the end-user choose which enrolled method
 * to challenge, auto-redirecting to the method's challenge step when a single method is enrolled.
 *
 * This purpose is only ever a follow-up of another purpose that requires MFA and whose user is enrolled; it is
 * therefore never a session's initiating purpose. It has no terminal effect: passing MFA is recorded during its
 * step, so the engine simply marks it complete once resolved.
 */
@Singleton
class MfaChallengeInteractiveFlowPurposeHandler : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.MFA_CHALLENGE

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        return if (session.mfaPassed) null else InteractiveFlowStep.MfaSelectionForChallenge
    }
}
