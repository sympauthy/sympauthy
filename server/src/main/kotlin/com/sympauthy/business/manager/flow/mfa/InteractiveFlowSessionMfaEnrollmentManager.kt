package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMfaEnrollmentMapper
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionMfaEnrollment
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionMfaEnrollmentEntity
import com.sympauthy.data.repository.InteractiveFlowSessionMfaEnrollmentRepository
import jakarta.transaction.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

/**
 * Manager in charge of the context of a standalone, client-initiated MFA enrollment attached to an
 * [InteractiveFlowSession]. It provides methods to:
 * - start an [InteractiveFlowPurpose.MFA_ENROLLMENT] session for an already-authenticated user, storing the
 *   URI to return them to on completion.
 * - fetch the enrollment record of a session (used to build the completion redirect).
 */
@Singleton
open class InteractiveFlowSessionMfaEnrollmentManager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val mfaEnrollmentRepository: InteractiveFlowSessionMfaEnrollmentRepository,
    @Inject private val mfaEnrollmentMapper: InteractiveFlowSessionMfaEnrollmentMapper,
) {

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
