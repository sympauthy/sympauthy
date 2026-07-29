package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

sealed class MfaRoutingResult

data class MfaAutoRedirect(val step: InteractiveFlowStep) : MfaRoutingResult()

data class MfaMethodSelection(
    val methods: List<AvailableMfaMethod>,
    val skippable: Boolean
) : MfaRoutingResult()

data class AvailableMfaMethod(val name: String, val step: InteractiveFlowStep)

@Singleton
class InteractiveFlowSessionMfaManager(
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val totpManager: TotpManager
) {

    /**
     * Returns the [MfaRoutingResult] describing what the end-user must do for the MFA step.
     *
     * Routing table:
     * | required | enrolled methods | Result                                                  |
     * |----------|------------------|---------------------------------------------------------|
     * | true     | 0                | Auto-redirect to TOTP enrollment                        |
     * | any      | 1                | Auto-redirect to enrolled method challenge               |
     * | any      | 2+ (future)      | Method selection: enrolled methods only, no skip         |
     * | false    | 0                | Method selection: all methods as enrollment offers + skip |
     */
    suspend fun getMfaResult(
        user: User
    ): MfaRoutingResult {
        val mfaConfig = uncheckedMfaConfig.orThrow()
        val enrollments = totpManager.findConfirmedEnrollments(user.id)

        return when {
            // User is enrolled in exactly one method — go straight to its challenge.
            enrollments.size == 1 ->
                MfaAutoRedirect(InteractiveFlowStep.MfaTotpChallenge)

            // User is enrolled in multiple methods (future) — let them pick, but no skip.
            enrollments.size > 1 ->
                MfaMethodSelection(
                    methods = listOf(
                        AvailableMfaMethod(name = "TOTP", step = InteractiveFlowStep.MfaTotpChallenge)
                    ),
                    skippable = false
                )

            // Not enrolled + required — force enrollment in the only available method.
            mfaConfig.required ->
                MfaAutoRedirect(InteractiveFlowStep.MfaTotpEnroll)

            // Not enrolled + optional — offer enrollment with the option to skip.
            else ->
                MfaMethodSelection(
                    methods = listOf(
                        AvailableMfaMethod(name = "TOTP", step = InteractiveFlowStep.MfaTotpEnroll)
                    ),
                    skippable = true
                )
        }
    }

    /**
     * Return the MFA purpose that must run for [session] before it can complete, or null when no MFA step is
     * required.
     *
     * Used by a purpose (e.g. OAuth2 authorize) to append the right MFA purpose once the user is known.
     * Evaluated only when MFA is enabled (TOTP configured):
     * - the user is already enrolled -> [InteractiveFlowPurpose.MFA_CHALLENGE] (takes precedence over required)
     * - the user signed up during this session -> [InteractiveFlowPurpose.MFA_ENROLLMENT] (skippable iff MFA
     *   is not required, enforced by [skipMfa])
     * - MFA is required and the user is not enrolled -> [InteractiveFlowPurpose.MFA_ENROLLMENT] (forced)
     * - otherwise (sign-in, not enrolled, not required) -> null
     */
    suspend fun selectRequiredMfaPurpose(session: OnGoingInteractiveFlowSession): InteractiveFlowPurpose? {
        val mfaConfig = uncheckedMfaConfig.orThrow()
        if (!mfaConfig.enabled) return null
        val userId = session.userId ?: return null
        val enrolled = totpManager.findConfirmedEnrollments(userId).isNotEmpty()
        return when {
            enrolled -> InteractiveFlowPurpose.MFA_CHALLENGE
            session.signedUp -> InteractiveFlowPurpose.MFA_ENROLLMENT
            mfaConfig.required -> InteractiveFlowPurpose.MFA_ENROLLMENT
            else -> null
        }
    }

    /**
     * Marks the MFA step as passed without the end-user completing a challenge.
     *
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if:
     * - MFA is required (`mfa.required=true`), or
     * - the end-user has already enrolled in at least one MFA method.
     */
    suspend fun skipMfa(session: OnGoingInteractiveFlowSession): OnGoingInteractiveFlowSession {
        if (uncheckedMfaConfig.orThrow().required) {
            throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed")
        }
        val userId = session.userId
            ?: throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed")
        if (totpManager.findConfirmedEnrollments(userId).isNotEmpty()) {
            throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed_when_enrolled")
        }
        return sessionManager.setMfaPassed(session)
    }
}
