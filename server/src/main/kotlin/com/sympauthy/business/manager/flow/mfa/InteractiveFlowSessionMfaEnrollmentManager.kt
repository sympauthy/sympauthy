package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMfaEnrollmentMapper
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionMfaEnrollment
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.InteractiveFlowSessionMfaEnrollmentEntity
import com.sympauthy.data.repository.InteractiveFlowSessionMfaEnrollmentRepository
import jakarta.transaction.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

/**
 * Manager owning the MFA **enrollment** purpose end to end. It provides methods to:
 * - route the enrollment method-selection step (choose a method to enroll, with a skip when optional).
 * - start an [InteractiveFlowPurpose.MFA_ENROLLMENT] session for an already-authenticated user (standalone,
 *   client-initiated enrollment), storing the URI to return them to on completion.
 * - fetch the enrollment record of a session (used to build the completion redirect).
 *
 * The challenge purpose is owned by [InteractiveFlowSessionMfaChallengeManager]; the two are kept isolated.
 */
@Singleton
open class InteractiveFlowSessionMfaEnrollmentManager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val mfaEnrollmentRepository: InteractiveFlowSessionMfaEnrollmentRepository,
    @Inject private val mfaEnrollmentMapper: InteractiveFlowSessionMfaEnrollmentMapper,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val totpManager: TotpManager,
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
     * Start a standalone MFA enrollment [InteractiveFlowSession] for the [userId] within the [flow],
     * attaching the [returnUri] the end-user is redirected back to once the enrollment completes, in a single
     * transaction.
     *
     * The [returnUri] must have been validated (e.g. against the initiating client's registered redirect
     * URIs) by the caller before reaching here.
     */
    @Transactional
    open suspend fun startMfaEnrollmentSession(
        userId: UUID,
        returnUri: URI,
        flow: AuthorizationFlow
    ): OnGoingInteractiveFlowSession {
        val session = sessionManager.newSession(listOf(InteractiveFlowPurpose.MFA_ENROLLMENT), flow)
                as? OnGoingInteractiveFlowSession
            ?: throw internalBusinessExceptionOf("flow.mfa.enrollment.start.not_ongoing")
        val withUser = sessionManager.setAuthenticatedUserId(session, userId)
        mfaEnrollmentRepository.save(
            InteractiveFlowSessionMfaEnrollmentEntity(
                sessionId = withUser.id,
                returnUri = returnUri.toString()
            )
        )
        return withUser
    }

    /**
     * Return the [InteractiveFlowSessionMfaEnrollment] record attached to the [session], or null if it is
     * not a standalone MFA enrollment session.
     */
    suspend fun fetchMfaEnrollmentOrNull(session: InteractiveFlowSession): InteractiveFlowSessionMfaEnrollment? {
        return mfaEnrollmentRepository.findBySessionId(session.id)
            ?.let(mfaEnrollmentMapper::toInteractiveFlowSessionMfaEnrollment)
    }

    /**
     * Return the [InteractiveFlowSessionMfaEnrollment] record attached to the [session], or throw an
     * unrecoverable [com.sympauthy.business.exception.BusinessException] if there is none.
     */
    suspend fun fetchMfaEnrollment(session: InteractiveFlowSession): InteractiveFlowSessionMfaEnrollment {
        return fetchMfaEnrollmentOrNull(session)
            ?: throw internalBusinessExceptionOf("flow.mfa.enrollment.missing")
    }
}
