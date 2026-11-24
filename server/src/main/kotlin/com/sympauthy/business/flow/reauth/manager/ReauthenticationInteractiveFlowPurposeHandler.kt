package com.sympauthy.business.flow.reauth.manager

import com.sympauthy.business.flow.manager.InteractiveFlowPurposeHandler
import com.sympauthy.business.flow.manager.InteractiveFlowSessionManager
import com.sympauthy.business.flow.model.InteractiveFlowPurpose
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.flow.model.InteractiveFlowStep
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.flow.model.PreparedTerminalEffect
import com.sympauthy.business.flow.model.PurposeDebugInformation
import com.sympauthy.business.mfa.manager.TotpManager
import com.sympauthy.business.user.manager.UserManager
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
 * re-authentication record. This handler reads that record to decide whether the sign-in step is
 * still needed, and once the flow completes, to move the person's last authentication to the moment it holds.
 *
 * When the account is MFA-enrolled, a fresh [InteractiveFlowPurpose.MFA_CHALLENGE] is inserted as a follow-up
 * (like [com.sympauthy.business.flow.auth.manager.OAuth2AuthorizeInteractiveFlowPurposeHandler] does) so
 * re-authentication is at least as strong as the account's own login.
 */
@Singleton
class ReauthenticationInteractiveFlowPurposeHandler(
    @Inject private val reauthenticationManager: InteractiveFlowSessionReauthenticationManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val totpManager: TotpManager,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val userManager: UserManager,
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.REAUTHENTICATION

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        val proven = reauthenticationManager.fetchReauthenticationOrNull(session)?.primaryCredentialProven ?: false
        return if (proven) null else InteractiveFlowStep.SignIn
    }

    /**
     * How far the gate has got: the moment the account's primary credential was proven, and nothing else. The
     * second factor this gate inserts is a purpose of its own and describes itself.
     */
    override suspend fun debugInformation(session: InteractiveFlowSession): List<PurposeDebugInformation> {
        val reauthentication = reauthenticationManager.fetchReauthenticationOrNull(session)
        return listOf(
            PurposeDebugInformation.Date(
                "primary_credential_proven_at",
                reauthentication?.primaryCredentialProvenDate
            )
        )
    }

    /**
     * Once the primary credential is proven, require a fresh TOTP challenge when the account is MFA-enrolled, by
     * declaring [InteractiveFlowPurpose.MFA_CHALLENGE] as a follow-up. Empty when MFA is disabled, the account is
     * not enrolled, or an MFA purpose is already present.
     */
    override suspend fun followUpPurposes(session: OnGoingInteractiveFlowSession): List<InteractiveFlowPurpose> {
        val hasMfaPurpose = session.purposes.any {
            it == InteractiveFlowPurpose.MFA_ENROLLMENT || it == InteractiveFlowPurpose.MFA_CHALLENGE
        }
        if (hasMfaPurpose) return emptyList()
        val mfaConfig = uncheckedMfaConfig.orThrow()
        if (!mfaConfig.enabled) return emptyList()
        session.userId ?: return emptyList()
        val enrolled = totpManager.isEnrolled(sessionManager.getUser(session))
        return if (enrolled) listOf(InteractiveFlowPurpose.MFA_CHALLENGE) else emptyList()
    }

    /**
     * Move the person's last authentication to the moment the gate saw their credential: they proved one, and
     * the flow it guarded is completing, even though that flow opens no grant.
     */
    override suspend fun prepareTerminalEffect(session: OnGoingInteractiveFlowSession): PreparedTerminalEffect {
        val userId = session.userId ?: return PreparedTerminalEffect.None
        val provenDate = reauthenticationManager.fetchReauthenticationOrNull(session)?.primaryCredentialProvenDate
            ?: return PreparedTerminalEffect.None
        return PreparedTerminalEffect.Apply { userManager.markAuthenticated(userId, provenDate) }
    }
}
