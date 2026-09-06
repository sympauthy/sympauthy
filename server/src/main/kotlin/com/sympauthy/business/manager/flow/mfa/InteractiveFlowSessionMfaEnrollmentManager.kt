package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.flow.confirm.InteractiveFlowSessionConfirmManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

/**
 * Manager owning the MFA **enrollment** purpose end to end. It provides methods to:
 * - route the enrollment method-selection step (choose a method to enroll, with a skip when optional).
 * - start an [InteractiveFlowPurpose.MFA_ENROLLMENT] session for an already-authenticated user (standalone,
 *   client-initiated enrollment), storing on the session the URI to return them to on completion and the
 *   optional URI to return them to on cancellation.
 *
 * The challenge purpose is owned by [InteractiveFlowSessionMfaChallengeManager]; the two are kept isolated.
 */
@Singleton
open class InteractiveFlowSessionMfaEnrollmentManager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val totpManager: TotpManager,
    @Inject private val confirmManager: InteractiveFlowSessionConfirmManager,
    @Inject private val userManager: UserManager,
) {

    /**
     * Return the [MfaRoutingResult] for the enrollment method-selection step of [session].
     *
     * TOTP is the only enrollable method today. When the enrollment can be skipped (see
     * [isEnrollmentSkippable]) the method is offered with a skip option; otherwise the end-user is
     * auto-redirected straight to the TOTP enrollment (there is no real choice to make).
     */
    suspend fun getEnrollmentRoutingResult(
        session: OnGoingInteractiveFlowSession
    ): MfaRoutingResult {
        return if (isEnrollmentSkippable(session)) {
            MfaMethodSelection(
                methods = listOf(
                    AvailableMfaMethod(name = "TOTP", step = InteractiveFlowStep.MfaTotpEnroll)
                ),
                skippable = true
            )
        } else {
            MfaAutoRedirect(InteractiveFlowStep.MfaTotpEnroll)
        }
    }

    /**
     * Whether the end-user may skip the MFA enrollment of [session].
     *
     * Skippable only when MFA is optional (`mfa.required=false`) and the enrollment is not standalone (the
     * session's initiating purpose is [InteractiveFlowPurpose.MFA_ENROLLMENT]) — a client that explicitly
     * requested an enrollment must not have it skipped. The user is, by construction of the enrollment
     * purpose, not yet enrolled.
     */
    suspend fun isEnrollmentSkippable(session: OnGoingInteractiveFlowSession): Boolean {
        if (uncheckedMfaConfig.orThrow().required) return false
        return session.initiatingPurpose != InteractiveFlowPurpose.MFA_ENROLLMENT
    }

    /**
     * Marks the MFA enrollment step as passed without the end-user enrolling a method.
     *
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if:
     * - MFA is required (`mfa.required=true`),
     * - the enrollment is a standalone, client-initiated one, or
     * - the end-user has already enrolled in at least one MFA method.
     */
    suspend fun skipMfa(session: OnGoingInteractiveFlowSession): OnGoingInteractiveFlowSession {
        if (uncheckedMfaConfig.orThrow().required) {
            throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed")
        }
        if (session.initiatingPurpose == InteractiveFlowPurpose.MFA_ENROLLMENT) {
            throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed_standalone")
        }
        val userId = session.userId
            ?: throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed")
        if (totpManager.findConfirmedEnrollments(userId).isNotEmpty()) {
            throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed_when_enrolled")
        }
        return sessionManager.setMfaPassed(session)
    }

    /**
     * Start a standalone MFA enrollment [InteractiveFlowSession] for the [userId] within the [flow], storing on
     * the session the [returnUri] the end-user is redirected back to once the enrollment completes (as a
     * [InteractiveFlowRedirectType.PLAIN] redirect) and the optional [cancelUri] they are redirected back to if
     * they cancel, in a single transaction.
     *
     * The enrollment is initiated on the end-user's behalf — by a client, or by an administrator when
     * [initiatingClientId] is null — so the session is gated by an [InteractiveFlowPurpose.CONFIRM] step
     * prepended in front of [InteractiveFlowPurpose.MFA_ENROLLMENT]: the end-user must explicitly approve the
     * enrollment before it begins. The confirm parameter — including [initiatingClientId] (null for an
     * administrator) — is stored on the session's confirm record in the same transaction.
     *
     * The [returnUri] and [cancelUri] must have been validated (e.g. against the named client's registered
     * redirect URIs) by the caller before reaching here.
     */
    @Transactional
    open suspend fun startMfaEnrollmentSession(
        userId: UUID,
        returnUri: URI,
        flow: AuthorizationFlow,
        initiatingClientId: String?,
        cancelUri: URI? = null,
    ): OnGoingInteractiveFlowSession {
        // The user id arrives from a client's access token or an administrator's path parameter, neither of
        // which is the session that would be entitled to an account still being signed up.
        userManager.checkPromoted(userId)
        val session = sessionManager.newSession(
            purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
            flow = flow,
            successRedirectUri = returnUri,
            redirectType = InteractiveFlowRedirectType.PLAIN,
            cancelRedirectUri = cancelUri,
        ) as? OnGoingInteractiveFlowSession
            ?: throw internalBusinessExceptionOf("flow.mfa.enrollment.start.not_ongoing")
        val sessionWithUser = sessionManager.setAuthenticatedUserId(session, userId)
        confirmManager.setConfirm(
            session = sessionWithUser,
            action = ConfirmActionType.ENROLL_MFA,
            clientId = initiatingClientId
        )
        return sessionWithUser
    }
}
