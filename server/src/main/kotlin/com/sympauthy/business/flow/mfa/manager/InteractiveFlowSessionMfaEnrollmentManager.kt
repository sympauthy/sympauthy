package com.sympauthy.business.flow.mfa.manager

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.flow.confirm.manager.InteractiveFlowSessionConfirmManager
import com.sympauthy.business.flow.manager.InteractiveFlowSessionManager
import com.sympauthy.business.flow.model.AuthorizationFlow
import com.sympauthy.business.flow.model.ConfirmActionType
import com.sympauthy.business.flow.model.InteractiveFlowHandBack
import com.sympauthy.business.flow.model.InteractiveFlowPurpose
import com.sympauthy.business.flow.model.InteractiveFlowRedirectType
import com.sympauthy.business.flow.model.InteractiveFlowStep
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.mfa.manager.TotpManager
import com.sympauthy.business.mfa.model.MfaMethodType
import com.sympauthy.business.user.manager.UserManager
import com.sympauthy.business.user.model.User
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

/**
 * Manager owning the MFA **enrollment** purpose end to end. It provides methods to:
 * - route the enrollment method-selection step (choose a method to enroll, with a skip when optional).
 * - start an [InteractiveFlowPurpose.MFA_ENROLLMENT] session for an already-authenticated user (standalone,
 *   client-initiated enrollment), storing on the session the URI to return them to however it ends.
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
                    AvailableMfaMethod(type = MfaMethodType.TOTP, step = InteractiveFlowStep.MfaTotpEnroll)
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
     * Start a standalone MFA enrollment [InteractiveFlowSession] for the [user] within the [flow], storing on
     * the session the [returnUri] the end-user is redirected back to however the enrollment ends (as a
     * [InteractiveFlowRedirectType.PLAIN] redirect), with the [returnState] the request carried, in a single
     * transaction.
     *
     * The enrollment is initiated on the end-user's behalf — by a client, or by an administrator when
     * [initiatingClientId] is null — so the session is gated by an [InteractiveFlowPurpose.CONFIRM] step
     * prepended in front of [InteractiveFlowPurpose.MFA_ENROLLMENT]: the end-user must explicitly approve the
     * enrollment before it begins. The confirm parameter — including [initiatingClientId] (null for an
     * administrator) — is stored on the session's confirm record in the same transaction.
     *
     * The [returnUri] must have been validated against the named client's return URIs by the caller before
     * reaching here.
     *
     * The session, its binding to [user] and its confirm record are written together, so a failure saving
     * the confirm record leaves no session behind for [user] — one no state was ever handed out for, which
     * the admin surface would list until it expired.
     */
    @Transactional
    open suspend fun startMfaEnrollmentSession(
        user: User,
        returnUri: URI,
        returnState: String?,
        flow: AuthorizationFlow,
        initiatingClientId: String?,
    ): OnGoingInteractiveFlowSession {
        // The user arrives from a client's access token or an administrator's path parameter, neither of
        // which is the session that would be entitled to an account still being signed up. By id though
        // [user] is in hand: promotion is the record's state, not its identity, so it is read again inside
        // this transaction rather than taken from a copy the caller may have read earlier.
        userManager.checkPromoted(user.id)
        val session = sessionManager.newSession(
            purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
            initiatingClientId = initiatingClientId,
            flow = flow,
            handBack = InteractiveFlowHandBack(returnUri, InteractiveFlowRedirectType.PLAIN, returnState),
        ) as? OnGoingInteractiveFlowSession
            ?: throw internalBusinessExceptionOf("flow.mfa.enrollment.start.not_ongoing")
        val sessionWithUser = sessionManager.setAuthenticatedUserId(session, user.id)
        confirmManager.setConfirm(
            session = sessionWithUser,
            action = ConfirmActionType.ENROLL_MFA,
            clientId = initiatingClientId
        )
        return sessionWithUser
    }
}
