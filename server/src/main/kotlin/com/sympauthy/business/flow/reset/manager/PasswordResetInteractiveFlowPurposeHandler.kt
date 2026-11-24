package com.sympauthy.business.flow.reset.manager

import com.sympauthy.business.claim.manager.CollectedClaimManager
import com.sympauthy.business.flow.manager.InteractiveFlowPurposeHandler
import com.sympauthy.business.flow.model.InteractiveFlowPurpose
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.flow.model.InteractiveFlowStep
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.flow.model.PreparedTerminalEffect
import com.sympauthy.business.flow.model.PurposeDebugInformation
import com.sympauthy.business.flow.oauth2.manager.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.grant.manager.GrantManager
import com.sympauthy.business.password.manager.PasswordManager
import com.sympauthy.business.password.manager.PasswordResetMessageSender
import com.sympauthy.business.user.manager.UserManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * [InteractiveFlowPurposeHandler] for the [InteractiveFlowPurpose.PASSWORD_RESET] gate: the end-user, who asked
 * from the sign-in step to reset a password they forgot, proves the account with the code sent to its proven
 * address and chooses its new password, before the authorization it stands in front of continues.
 *
 * Proving the account establishes the session's user, so once the new password is chosen the authorization goes on
 * as after a password sign-in, its second factor included. What the reset changes is applied only as the session
 * completes, so a session abandoned or failed past the code leaves the account as it was.
 */
@Singleton
class PasswordResetInteractiveFlowPurposeHandler(
    @Inject private val passwordResetManager: InteractiveFlowSessionPasswordResetManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val passwordManager: PasswordManager,
    @Inject private val grantManager: GrantManager,
    @Inject private val userManager: UserManager,
    @Inject private val messageSender: PasswordResetMessageSender
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.PASSWORD_RESET

    /**
     * The code page until a code proves the account, then the page choosing the new password until one is chosen.
     *
     * A session whose user was established otherwise — the end-user went back and signed in before proving any
     * code — needs neither: the reset is resolved, and has nothing to apply.
     */
    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        val passwordReset = passwordResetManager.fetchPasswordResetOrNull(session) ?: return null
        return when {
            passwordReset.codeProvenDate == null ->
                if (session.userId == null) InteractiveFlowStep.ResetPasswordValidate else null

            passwordReset.userId != session.userId -> null
            passwordReset.newPassword == null -> InteractiveFlowStep.ResetPasswordChoose
            else -> null
        }
    }

    /**
     * How far the reset has got: the account a code went to and the moments of each step. Neither the identifier
     * typed, which may be somebody else's address, nor anything of the new password is published.
     */
    override suspend fun debugInformation(session: InteractiveFlowSession): List<PurposeDebugInformation> {
        val passwordReset = passwordResetManager.fetchPasswordResetOrNull(session)
        return listOf(
            PurposeDebugInformation.Text("code_sent_to_user_id", passwordReset?.userId?.toString()),
            PurposeDebugInformation.Date("requested_at", passwordReset?.requestedDate),
            PurposeDebugInformation.Date("code_proven_at", passwordReset?.codeProvenDate),
            PurposeDebugInformation.Date("password_chosen_at", passwordReset?.passwordChosenDate)
        )
    }

    /**
     * Replace the account's passwords with the one chosen, tie the grant the authorization opens to it, revoke the
     * grants the replaced ones opened, and tell the account's proven address its password changed.
     *
     * Its writes are applied before the authorization's own, since this gate stands in front of it: the grant the
     * authorization opens records the new password, and is not among those revoked. The mail is written with them,
     * so it leaves only once the completion has committed, and only for the request that committed it. Nothing is
     * applied for a reset that did not get as far as a new password for the session's user.
     */
    override suspend fun prepareTerminalEffect(session: OnGoingInteractiveFlowSession): PreparedTerminalEffect {
        val passwordReset = passwordResetManager.fetchPasswordResetOrNull(session)
            ?: return PreparedTerminalEffect.None
        val userId = session.userId ?: return PreparedTerminalEffect.None
        val newPassword = passwordReset.newPassword
        if (passwordReset.codeProvenDate == null || passwordReset.userId != userId || newPassword == null) {
            return PreparedTerminalEffect.None
        }
        val user = userManager.findById(userId)
        val address = collectedClaimManager.findVerifiedEmailOrNull(userId)
        val changedAt = LocalDateTime.now()
        return PreparedTerminalEffect.Apply {
            val replacement = passwordManager.replacePasswords(user, newPassword)
            oauth2Manager.setPasswordId(session, replacement.passwordId)
            grantManager.revokeGrantsByPasswords(user.id, replacement.replacedPasswordIds, changedAt)
            address?.let { messageSender.sendPasswordChanged(it, changedAt) }
        }
    }
}
