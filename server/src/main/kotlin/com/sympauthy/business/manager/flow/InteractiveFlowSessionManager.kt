package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMapper
import com.sympauthy.business.model.flow.*
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

/**
 * Manager in charge of the lifecycle of the flow-generic [InteractiveFlowSession].
 * It provides methods to:
 * - create a session.
 * - encode / verify the signed state that identifies a session.
 * - modify the session: user id, MFA, error, completion.
 *
 * Concern-specific state (the OAuth2 request, the third-party provider authorization) is handled by the
 * dedicated [InteractiveFlowSessionOAuth2Manager] / [InteractiveFlowSessionProviderManager], which build
 * on top of this manager.
 *
 * Note: For separation of concerns, this manager does not handle any logic of the interactive flow itself.
 * Managers handling those logics are in the flow package.
 */
@Singleton
class InteractiveFlowSessionManager(
    @Inject private val userManager: UserManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val sessionRepository: InteractiveFlowSessionRepository,
    @Inject private val sessionMapper: InteractiveFlowSessionMapper,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Create a new [InteractiveFlowSession] for the end-user and save it in the database.
     *
     * The session is saved even if it is created in error (i.e. [error] is non-null), in which case a
     * [FailedInteractiveFlowSession] is returned.
     *
     * This method is not transactional on its own so it can participate in the caller's transaction (e.g.
     * to persist an attached record atomically with the session).
     */
    suspend fun newSession(
        purpose: FlowPurpose,
        flow: AuthorizationFlow? = null,
        error: BusinessException? = null
    ): InteractiveFlowSession {
        val now = LocalDateTime.now()
        val entity = InteractiveFlowSessionEntity(
            purpose = purpose.name,
            flowId = flow?.id,
            sessionDate = now,
            expirationDate = now.plus(uncheckedAuthConfig.orThrow().authorizationCode.expiration),

            errorDate = error?.let { now },
            errorDetailsId = error?.detailsId,
            errorDescriptionId = error?.descriptionId,
            errorValues = error?.values,
        )
        sessionRepository.save(entity)

        return sessionMapper.toInteractiveFlowSession(entity)
    }

    suspend fun encodeState(session: InteractiveFlowSession): String {
        return jwtManager.create(STATE_KEY_NAME) {
            subject(session.id.toString())
        }
    }

    /**
     * Return a [SuccessVerifyEncodedStateResult] containing the [InteractiveFlowSession] that created the
     * [state] after verifying the [state] has not been tempered with.
     * Otherwise, return a [FailedVerifyEncodedStateResult] with the appropriate error details.
     *
     * Note: This method does not check the status of the [InteractiveFlowSession] in order to let the
     * caller decide how to handle a completed or failed session.
     */
    suspend fun verifyEncodedInternalState(state: String?): VerifyEncodedStateResult {
        if (state.isNullOrBlank()) {
            return FailedVerifyEncodedStateResult(
                detailsId = "auth.interactive_flow_session.validate.missing_state",
                descriptionId = "description.oauth2.invalid_state"
            )
        }
        val jwt = jwtManager.decodeAndVerifyOrNull(STATE_KEY_NAME, state) ?: return FailedVerifyEncodedStateResult(
            detailsId = "auth.interactive_flow_session.validate.wrong_signature",
            descriptionId = "description.oauth2.invalid_state"
        )
        val sessionId = try {
            UUID.fromString(jwt.subject)
        } catch (e: IllegalArgumentException) {
            return FailedVerifyEncodedStateResult(
                detailsId = "auth.interactive_flow_session.validate.invalid_subject",
                descriptionId = "description.oauth2.invalid_state"
            )
        }
        val session = sessionRepository.findById(sessionId)
            ?.let(sessionMapper::toInteractiveFlowSession)

        return if (session != null) {
            SuccessVerifyEncodedStateResult(session)
        } else {
            FailedVerifyEncodedStateResult(
                detailsId = "auth.interactive_flow_session.validate.missing_session",
                descriptionId = "description.oauth2.expired",
                values = mapOf("sessionId" to sessionId.toString())
            )
        }
    }

    /**
     * Associate the user that has been authenticated to its [InteractiveFlowSession].
     */
    suspend fun setAuthenticatedUserId(
        session: OnGoingInteractiveFlowSession,
        userId: UUID
    ): OnGoingInteractiveFlowSession {
        sessionRepository.updateUserId(
            id = session.id,
            userId = userId
        )
        return session.copy(
            userId = userId
        )
    }

    /**
     * Records that the end-user has passed the MFA step for this [session].
     * Persists the mfaPassedDate and returns the updated session.
     */
    suspend fun setMfaPassed(
        session: OnGoingInteractiveFlowSession
    ): OnGoingInteractiveFlowSession {
        val mfaPassedDate = LocalDateTime.now()
        sessionRepository.updateMfaPassedDate(
            id = session.id,
            mfaPassedDate = mfaPassedDate
        )
        return session.copy(mfaPassedDate = mfaPassedDate)
    }

    /**
     * Set and save the error if it is non-recoverable to prevent further usage of the [session].
     * Do nothing if the [error] is recoverable.
     */
    suspend fun markAsFailedIfNotRecoverable(
        session: OnGoingInteractiveFlowSession,
        error: BusinessException
    ): InteractiveFlowSession {
        if (error.recoverable) return session

        val errorDate = LocalDateTime.now()
        sessionRepository.updateError(
            id = session.id,
            errorDate = errorDate,
            errorDetailsId = error.detailsId,
            errorDescriptionId = error.descriptionId,
            errorValues = error.values
        )
        return FailedInteractiveFlowSession(
            id = session.id,
            purpose = session.purpose,
            flowId = session.flowId,
            errorDate = errorDate,
            errorDetailsId = error.detailsId,
            errorDescriptionId = error.descriptionId,
            errorValues = error.values,
            expirationDate = session.expirationDate
        )
    }

    /**
     * Set and save the completion date of the [session] and return the completed session.
     *
     * The presence of the concern-specific completion invariants (e.g. consent / grant for an OAuth2
     * session) is enforced by the matching manager when the attached record is read.
     */
    suspend fun markAsComplete(
        session: OnGoingInteractiveFlowSession
    ): CompletedInteractiveFlowSession {
        val userId = session.userId ?: throw businessExceptionOf(
            "auth.interactive_flow_session.complete.missing_user"
        )
        val completeDate = LocalDateTime.now()
        sessionRepository.updateCompleteDate(
            id = session.id,
            completeDate = completeDate
        )
        return CompletedInteractiveFlowSession(
            id = session.id,
            purpose = session.purpose,
            flowId = session.flowId,
            expirationDate = session.expirationDate,
            sessionDate = session.sessionDate,
            userId = userId,
            mfaPassedDate = session.mfaPassedDate,
            completeDate = completeDate
        )
    }

    /**
     * Return the [User] associated to the [session] or null if:
     * - there is no user associated to the ongoing [session].
     * - the [session] has failed.
     *
     * Throws an unrecoverable [BusinessException] if the user id is corrupted and cannot be found in the
     * database anymore.
     */
    suspend fun getUserOrNull(session: InteractiveFlowSession): User? {
        return when (session) {
            is OnGoingInteractiveFlowSession -> session.userId?.let { userManager.findByIdOrNull(it) }
            is CompletedInteractiveFlowSession -> userManager.findByIdOrNull(session.userId)
            is FailedInteractiveFlowSession -> null
        }
    }

    /**
     * Return the [User] associated to the [session] or throw an unrecoverable [BusinessException] if:
     * - there is no user associated to the ongoing [session].
     * - the [session] has failed.
     * - the user id is corrupted and cannot be found in the database anymore.
     */
    suspend fun getUser(
        session: InteractiveFlowSession
    ): User {
        return getUserOrNull(session) ?: throw businessExceptionOf(
            detailsId = "auth.interactive_flow_session.user.missing"
        )
    }

    suspend fun findByCodeOrNull(code: String): InteractiveFlowSession? {
        val session = sessionRepository.findByCode(code)
            ?.let(sessionMapper::toInteractiveFlowSession)
        return if (session?.expired == false) {
            session
        } else null
    }

    companion object {
        /**
         * Name of the cryptographic key used to sign the state.
         */
        const val STATE_KEY_NAME = "state"
    }
}

sealed class VerifyEncodedStateResult

class SuccessVerifyEncodedStateResult(
    val session: InteractiveFlowSession
) : VerifyEncodedStateResult()

class FailedVerifyEncodedStateResult(
    val detailsId: String,
    val descriptionId: String? = null,
    val values: Map<String, String>? = null
) : VerifyEncodedStateResult()
