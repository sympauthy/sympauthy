package com.sympauthy.business.manager.flow.reauth

import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * [InteractiveFlowPurposeHandler] for the [InteractiveFlowPurpose.REAUTHENTICATION] gate: it makes the end-user
 * prove they control the account they are **already** identified as ([OnGoingInteractiveFlowSession.userId] is
 * fixed before this purpose runs), before a sensitive step proceeds.
 *
 * It reuses the sign-in step ([InteractiveFlowStep.SignIn]) — the sign-in password/provider paths detect the
 * active purpose and switch to **confirm, never establish** semantics, then record the proof on the session's
 * re-authentication record. This handler stays pure: it only reads that record to decide whether the sign-in
 * step is still needed.
 *
 * When the account is MFA-enrolled, a fresh [InteractiveFlowPurpose.MFA_CHALLENGE] is appended as a follow-up
 * (like [com.sympauthy.business.manager.flow.auth.OAuth2AuthorizeInteractiveFlowPurposeHandler] does) so
 * re-authentication is at least as strong as the account's own login.
 */
@Singleton
class ReauthenticationInteractiveFlowPurposeHandler(
    @Inject private val reauthenticationManager: InteractiveFlowSessionReauthenticationManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val totpManager: TotpManager,
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.REAUTHENTICATION

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        val proven = reauthenticationManager.fetchReauthenticationOrNull(session)?.primaryCredentialProven ?: false
        return if (proven) null else InteractiveFlowStep.SignIn
    }

    /**
     * Once the primary credential is proven, require a fresh TOTP challenge when the account is MFA-enrolled, by
     * appending [InteractiveFlowPurpose.MFA_CHALLENGE]. Empty when MFA is disabled, the account is not enrolled,
     * or an MFA purpose is already present.
     */
    override suspend fun followUpPurposes(session: OnGoingInteractiveFlowSession): List<InteractiveFlowPurpose> {
        val hasMfaPurpose = session.purposes.any {
            it == InteractiveFlowPurpose.MFA_ENROLLMENT || it == InteractiveFlowPurpose.MFA_CHALLENGE
        }
        if (hasMfaPurpose) return emptyList()
        val mfaConfig = uncheckedMfaConfig.orThrow()
        if (!mfaConfig.enabled) return emptyList()
        val userId = session.userId ?: return emptyList()
        return if (totpManager.isEnrolled(userId)) listOf(InteractiveFlowPurpose.MFA_CHALLENGE) else emptyList()
    }
}
