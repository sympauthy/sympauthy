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
import com.sympauthy.util.wireName
import jakarta.inject.Inject
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
class MfaChallengeInteractiveFlowPurposeHandler(
    @Inject private val totpManager: TotpManager,
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.MFA_CHALLENGE

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        return if (session.mfaPassed) null else InteractiveFlowStep.MfaSelectionForChallenge
    }

    /**
     * Whether the challenge has been passed, and which methods there are to pass it with — a challenge with
     * nothing to offer being the way this purpose stalls.
     *
     * Only the **types** of the account's methods are read, so no TOTP seed and no recovery code ever reaches
     * here: one published here is a second factor defeated for good.
     *
     * A session with no user has no methods to read, and answers with nothing. An account that has enrolled
     * none answers an empty list instead — which is how this purpose stalls, and it must not read as a value
     * nobody could look up.
     */
    override suspend fun debugInformation(session: InteractiveFlowSession): List<PurposeDebugInformation> {
        val challengeable = session.userIdOrNull?.let { userId ->
            totpManager.findEnrolledMethodTypes(userId).map { it.wireName }
        }
        return listOf(
            PurposeDebugInformation.Date("mfa_passed_at", session.mfaPassedDateOrNull),
            PurposeDebugInformation.TextList("challengeable_methods", challengeable)
        )
    }
}
