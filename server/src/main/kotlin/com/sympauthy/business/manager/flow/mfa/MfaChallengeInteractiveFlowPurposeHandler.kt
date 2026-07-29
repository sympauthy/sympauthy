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
 * [InteractiveFlowPurposeHandler] for [InteractiveFlowPurpose.MFA_CHALLENGE].
 *
 * Drives the end-user through a TOTP challenge with an already-enrolled authenticator. The purpose resolves
 * once the end-user has passed the MFA step for the session — set when a valid TOTP code is submitted.
 *
 * This purpose is only ever appended to another purpose that requires MFA and whose user is enrolled; it is
 * therefore never a session's initiating purpose. [complete] is implemented for interface completeness.
 */
@Singleton
class MfaChallengeInteractiveFlowPurposeHandler(
    @Inject private val sessionManager: InteractiveFlowSessionManager
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.MFA_CHALLENGE

    override suspend fun getNextStep(session: OnGoingInteractiveFlowSession): InteractiveFlowPurposeStepResult {
        return if (session.mfaPassed) {
            InteractiveFlowPurposeStepResult.Resolved(session)
        } else {
            InteractiveFlowPurposeStepResult.Pending(session, InteractiveFlowStep.MfaTotpChallenge)
        }
    }

    override suspend fun complete(session: OnGoingInteractiveFlowSession): InteractiveFlowSession {
        return sessionManager.markAsComplete(session)
    }
}
