package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.flow.recordAttemptOfDeclaredKind
import com.sympauthy.business.manager.flow.reset.InteractiveFlowSessionPasswordResetManager
import com.sympauthy.business.manager.password.PasswordManager
import com.sympauthy.business.manager.password.PasswordResetMessageSender
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.security.UserSecurityContextManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.validationcode.ValidationCodeGenerator
import com.sympauthy.business.manager.validationcode.ValidationCodeManager
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionPasswordReset
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics.Companion.PASSWORD
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics.Companion.RESET
import com.sympauthy.util.min
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

/**
 * Manager in charge of an end-user resetting, from the sign-in step of an interactive auth flow, a password they
 * forgot: asking for a code, proving the account with it, and choosing the new password.
 *
 * **Every identifier is answered alike.** Whatever account holds the identifier typed, if any, the end-user is
 * taken to the code page, and a code typed there that proves nothing is refused as a wrong code is. What differs is
 * only what reaches an inbox: a code to the proven email address of an account holding a password, a notice where
 * the account holds none, a notice to an email address no account uses, and nothing otherwise.
 *
 * The new password is held on the session's reset record until the session completes, which is when
 * [com.sympauthy.business.manager.flow.reset.PasswordResetInteractiveFlowPurposeHandler] applies it.
 */
@Singleton
open class InteractiveAuthFlowSessionPasswordResetManager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val passwordResetManager: InteractiveFlowSessionPasswordResetManager,
    @Inject private val passwordFlowManager: InteractiveAuthFlowSessionPasswordManager,
    @Inject private val passwordManager: PasswordManager,
    @Inject private val messageSender: PasswordResetMessageSender,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val userManager: UserManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val providerManager: ProviderManager,
    @Inject private val validationCodeGenerator: ValidationCodeGenerator,
    @Inject private val validationCodeManager: ValidationCodeManager,
    @Inject private val userSecurityContextManager: UserSecurityContextManager,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig,
    @Inject private val authenticationMetrics: AuthenticationMetrics
) {

    /**
     * Whether [flow] offers a reset: it names its reset pages, this server can send mail, and the end-user can
     * sign in with a password at all.
     */
    fun isOffered(flow: InteractiveFlow): Boolean =
        flow.resetPassword != null && messageSender.enabled && passwordFlowManager.signInEnabled

    /**
     * Throws the internal `flow.password.reset.disabled` unless [flow] [offers][isOffered] a reset: a reset step
     * is reached only through a link the sign-in step gives where it does.
     */
    fun checkOffered(flow: InteractiveFlow) {
        if (!isOffered(flow)) {
            throw internalBusinessExceptionOf("flow.password.reset.disabled")
        }
    }

    /**
     * Start resetting, in [session] of [flow], the password of whichever account holds [login], and return the
     * session with the [InteractiveFlowPurpose.PASSWORD_RESET] gate in front of the authorization.
     *
     * A code goes to the proven email address of that account where it is enabled and holds a password. Asked
     * again, for another identifier or the same one, the reset starts over: a code sent before proves only the
     * account it was sent to, and only while that account is the one asked for.
     *
     * Asking again is held to the delay [resendCode] is, or a script asking in a loop would send a mail to any
     * address at the rate it asks. Asked too soon, nothing is sent: the same identifier keeps the reset as it was,
     * and another is recorded with no code sent, so that a resend once the delay has passed goes to it.
     *
     * Throws the recoverable `flow.password.reset.missing_login` where [login] is blank, and the internal
     * `flow.password.reset.disabled` where [flow] offers no reset. A session whose user is already established
     * is answered unchanged: asking for a code no longer applies there.
     *
     * It is not one transaction, because the mail queue hands a mail to its sender as soon as it is queued: one
     * queued inside a transaction is sent before the transaction has decided anything, and its queue row, written
     * by that transaction, outlives the sender's deletion of it to be sent again on the next startup. So the mail
     * goes last, once the reset is recorded. Every write before it belongs to the session, and a gate with no
     * record resolves with nothing to apply, so a request failing part way sends nothing and resets nothing.
     */
    suspend fun requestCode(
        session: OnGoingInteractiveFlowSession,
        flow: InteractiveFlow,
        login: String?
    ): InteractiveFlowSession {
        checkOffered(flow)
        if (session.userId != null) return session
        if (login.isNullOrBlank()) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.password.reset.missing_login",
                descriptionId = "description.flow.password.reset.missing_login"
            )
        }
        val gated = if (InteractiveFlowPurpose.PASSWORD_RESET in session.purposes) {
            session
        } else {
            sessionManager.insertPurposeBefore(
                session = session,
                purpose = InteractiveFlowPurpose.PASSWORD_RESET,
                beforePurpose = session.initiatingPurpose
            )
        }
        val previous = passwordResetManager.fetchPasswordResetOrNull(gated)
        if (previous != null && !canResend(gated, previous)) {
            if (previous.login != login) {
                passwordResetManager.savePasswordReset(gated, login, userId = null, previous.requestedDate)
            }
            return gated
        }
        val mail = prepareResetMail(gated, login)
        passwordResetManager.savePasswordReset(gated, login, mail.userId)
        sendResetMail(mail)
        return gated
    }

    /**
     * Send the code, or the notice, the reset of [login] in [session] calls for once more, where
     * `advanced.validation-code.resend-delay` has passed since the last was asked for, and return whether one
     * was asked for again, beside the record as it now stands.
     *
     * The delay runs whatever the last request sent, nothing included, so that how soon another may be asked for
     * says nothing about the account. Where no delay is configured, another may be asked for once the code would
     * have expired.
     *
     * Throws the internal `flow.password.reset.disabled` where [flow] offers no reset. Nothing is asked for again
     * where [session] has no reset, one whose code has proven the account already, or one whose user is
     * established otherwise: the reset is resolved there, and a code would prove nothing.
     *
     * The mail goes last and outside any transaction, for the reason [requestCode] gives.
     */
    suspend fun resendCode(
        session: OnGoingInteractiveFlowSession,
        flow: InteractiveFlow
    ): ResendResult {
        checkOffered(flow)
        val passwordReset = passwordResetManager.fetchPasswordResetOrNull(session)
        if (
            passwordReset == null ||
            passwordReset.codeProvenDate != null ||
            session.userId != null ||
            !canResend(session, passwordReset)
        ) {
            return ResendResult(resent = false, passwordReset = passwordReset)
        }
        val mail = prepareResetMail(session, passwordReset.login)
        val saved = passwordResetManager.savePasswordReset(session, passwordReset.login, mail.userId)
        sendResetMail(mail)
        return ResendResult(resent = true, passwordReset = saved)
    }

    /**
     * Return when another code may be asked for the reset [passwordReset] holds, or null where it may be once the
     * code would have expired, `advanced.validation-code.resend-delay` configuring no delay.
     */
    fun getResendDate(passwordReset: InteractiveFlowSessionPasswordReset): LocalDateTime? {
        return uncheckedAdvancedConfig.orThrow().validationCode.resendDelay?.let(passwordReset.requestedDate::plus)
    }

    internal fun canResend(
        session: OnGoingInteractiveFlowSession,
        passwordReset: InteractiveFlowSessionPasswordReset
    ): Boolean {
        val now = LocalDateTime.now()
        val resendDate = getResendDate(passwordReset)
        if (resendDate != null) return !now.isBefore(resendDate)
        val expiration = uncheckedAdvancedConfig.orThrow().validationCode.expiration
        return !now.isBefore(min(session.expirationDate, passwordReset.requestedDate.plus(expiration)))
    }

    /**
     * Prove, with the [code] the end-user typed, the account the reset of [session] sent a code to, and return the
     * session that account now signs in.
     *
     * The code proves the account as a password sign-in does: the session's user is established, the place the
     * request came from, [observedRequest], is marked as one a credential was proven at, and the moment is the
     * authorization's authentication date. No password is tied to it yet; the new one is, once it is written.
     *
     * Recorded as an attempt of `kind` `reset`, unless it reached a session where proving the account no longer
     * applies, which authenticates nobody.
     *
     * Throws the recoverable `flow.password.reset.invalid_code` where [code] matches no code sent to that account
     * in [session], matches one that has expired, or where no code was sent at all — the identifier typed being
     * one no account holds, or one whose account may not be reset — so that the code page answers no more than
     * the page before it. Throws the internal `flow.password.reset.disabled` where [flow] offers no reset.
     */
    suspend fun validateCode(
        session: OnGoingInteractiveFlowSession,
        flow: InteractiveFlow,
        code: String?,
        observedRequest: ObservedRequest
    ): InteractiveFlowSession = authenticationMetrics.recordAttemptOfDeclaredKind(
        PASSWORD, null, RESET
    ) { declareKind ->
        checkOffered(flow)
        val passwordReset = passwordResetManager.fetchPasswordResetOrNull(session)
        if (passwordReset == null || session.userId != null) {
            declareKind(null)
            return@recordAttemptOfDeclaredKind session
        }

        val user = findProvenUserOrNull(session, passwordReset, code) ?: throw recoverableBusinessExceptionOf(
            detailsId = "flow.password.reset.invalid_code",
            descriptionId = "description.flow.password.reset.invalid_code"
        )

        // Stamped before the flow advances, as a password sign-in stamps it: completing the flow folds it. Outside
        // the transaction below, since the stamp fails quietly and a failed statement aborts a transaction on
        // PostgreSQL.
        userSecurityContextManager.markProven(session.id, observedRequest)
        signInProvenAccount(session, passwordReset, user)
    }

    /**
     * Sign the account [user] that a code proved into [session], dating the authentication now, and record the code
     * of [passwordReset] proven, all or none: a session signed in with no code recorded proven would resolve the
     * reset with no new password, and carry on signed in by the code alone.
     */
    @Transactional
    internal open suspend fun signInProvenAccount(
        session: OnGoingInteractiveFlowSession,
        passwordReset: InteractiveFlowSessionPasswordReset,
        user: User
    ): OnGoingInteractiveFlowSession {
        val authenticated = sessionManager.setAuthenticatedUserId(session, user.id)
        oauth2Manager.setAuthenticationDate(authenticated, passwordId = null)
        passwordResetManager.markCodeProven(authenticated, passwordReset)
        // A code proves the account once: one typed again would prove nothing a second session could use.
        validationCodeManager.revokeValidationCodes(findResetCodes(session, user.id))
        return authenticated
    }

    /**
     * Return the account [passwordReset] sent a code to where [code] is one of the codes it was sent in [session]
     * and has not expired, and where the account may still be reset. Otherwise, return null.
     */
    internal suspend fun findProvenUserOrNull(
        session: OnGoingInteractiveFlowSession,
        passwordReset: InteractiveFlowSessionPasswordReset,
        code: String?
    ): User? {
        val userId = passwordReset.userId ?: return null
        if (code.isNullOrBlank()) return null
        val matching = findResetCodes(session, userId).firstOrNull { it.code == code } ?: return null
        if (matching.expired) return null
        return userManager.findByIdOrNull(userId)?.takeIf { it.status == UserStatus.ENABLED }
    }

    private suspend fun findResetCodes(session: InteractiveFlowSession, userId: UUID) =
        validationCodeManager.findCodeForReasonsDuringSession(
            session = session,
            reasons = listOf(ValidationCodeReason.RESET_PASSWORD),
            includesExpired = true
        ).filter { it.userId == userId }

    /**
     * Record the [password] the end-user chose for the account a code proved in [session], and return the session.
     * The account keeps signing in with its own until the session completes.
     *
     * Throws the recoverable `flow.password.reset.missing_password` where [password] is blank, whatever the
     * password rules refuse a sign-up password with otherwise, and the internal `flow.password.reset.disabled`
     * where [flow] offers no reset. A session whose reset has no code proven yet is answered unchanged: choosing a
     * password does not apply there.
     */
    suspend fun choosePassword(
        session: OnGoingInteractiveFlowSession,
        flow: InteractiveFlow,
        password: String?
    ): InteractiveFlowSession {
        checkOffered(flow)
        val passwordReset = passwordResetManager.fetchPasswordResetOrNull(session)
        if (passwordReset?.codeProvenDate == null || passwordReset.userId != session.userId) return session
        if (password.isNullOrBlank()) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.password.reset.missing_password",
                descriptionId = "description.flow.password.reset.missing_password"
            )
        }
        passwordResetManager.markPasswordChosen(session, passwordReset, passwordManager.hashPassword(password))
        return session
    }

    /**
     * Return what a reset of [login] in [session] calls for, generating the code where it is one, without sending
     * anything.
     *
     * - An account that is enabled, has a proven email address and holds a password is sent a code there.
     * - An account that is enabled, has a proven email address and holds no password is told there that it signs
     *   in through its providers.
     * - An email address no account uses is told that none does.
     * - Anything else is sent nothing: an account disabled or without a proven address, and an identifier that
     *   is not an email address.
     */
    internal suspend fun prepareResetMail(session: OnGoingInteractiveFlowSession, login: String): ResetMail {
        val folded = collectedClaimManager.getIdentifierFoldedValuesOf(login)
        val user = userManager.findByAnyIdentifierClaimValue(folded)
        if (user == null) {
            // To the address as an account would hold it, not as it was typed.
            return collectedClaimManager.getIdentifierCleanedValueOrNull(OpenIdConnectClaimId.EMAIL, login)
                ?.let(ResetMail::NoAccount)
                ?: ResetMail.None
        }
        if (user.status != UserStatus.ENABLED) return ResetMail.None
        // An address nobody proved may be a typo, and a code sent there hands the account to its owner.
        val address = collectedClaimManager.findVerifiedEmailOrNull(user.id) ?: return ResetMail.None
        // An expired password is still one to replace, and the account it locks out the one a reset is for.
        if (!passwordManager.hasPassword(user, includesExpired = true)) {
            return ResetMail.NoPassword(address, listLinkedProviderNames(user))
        }
        val code = validationCodeGenerator.generateValidationCode(
            user = user,
            session = session,
            media = ValidationCodeMedia.EMAIL,
            reasons = listOf(ValidationCodeReason.RESET_PASSWORD)
        )
        return ResetMail.Code(address, code, user.id)
    }

    private suspend fun sendResetMail(mail: ResetMail) {
        when (mail) {
            is ResetMail.Code -> messageSender.sendCode(mail.address, mail.code)
            is ResetMail.NoPassword -> messageSender.sendNoPassword(mail.address, mail.providerNames)
            is ResetMail.NoAccount -> messageSender.sendNoAccount(mail.address)
            ResetMail.None -> Unit
        }
    }

    private suspend fun listLinkedProviderNames(user: User): List<String> {
        val linkedProviderIds = providerClaimsManager.findByUserId(user.id).map { it.providerId }.toSet()
        return providerManager.listEnabledProviders()
            .filter { it.id in linkedProviderIds }
            .map { it.name }
    }

    /**
     * What a reset sends to the identifier asked for, as [prepareResetMail] decided it.
     */
    internal sealed interface ResetMail {
        /**
         * The account a code goes to, or null where none does.
         */
        val userId: UUID? get() = null

        class Code(val address: String, val code: ValidationCode, override val userId: UUID) : ResetMail
        class NoPassword(val address: String, val providerNames: List<String>) : ResetMail
        class NoAccount(val address: String) : ResetMail
        data object None : ResetMail
    }

    data class ResendResult(
        /**
         * True if a code, or the notice standing in for one, was asked for again.
         */
        val resent: Boolean,
        /**
         * The reset as it stands once asked for again, or as it stood where it was not. Null where the session has
         * none.
         */
        val passwordReset: InteractiveFlowSessionPasswordReset?
    )
}
