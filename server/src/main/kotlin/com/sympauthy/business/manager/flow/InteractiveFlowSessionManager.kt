package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
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
import java.net.URI
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
        purposes: List<InteractiveFlowPurpose>,
        initiatingPurpose: InteractiveFlowPurpose,
        flow: AuthorizationFlow? = null,
        successRedirectUri: URI? = null,
        redirectType: InteractiveFlowRedirectType? = null,
        cancelRedirectUri: URI? = null,
        error: BusinessException? = null
    ): InteractiveFlowSession {
        val now = LocalDateTime.now()
        val entity = InteractiveFlowSessionEntity(
            purposes = purposes.map(InteractiveFlowPurpose::name).toTypedArray(),
            initiatingPurpose = initiatingPurpose.name,
            flowId = flow?.id,
            sessionDate = now,
            expirationDate = now.plus(uncheckedAuthConfig.orThrow().authorizationCode.expiration),

            successRedirectUri = successRedirectUri?.toString(),
            redirectType = redirectType?.name,
            cancelRedirectUri = cancelRedirectUri?.toString(),

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
     *
     * [signedUp] records whether the user was created (signed up) during this session, so a later step (e.g.
     * the MFA-purpose selection) can branch on sign-up vs sign-in.
     */
    suspend fun setAuthenticatedUserId(
        session: OnGoingInteractiveFlowSession,
        userId: UUID,
        signedUp: Boolean = false
    ): OnGoingInteractiveFlowSession {
        sessionRepository.updateUserId(
            id = session.id,
            userId = userId,
            signedUp = signedUp
        )
        return session.copy(
            userId = userId,
            signedUp = signedUp
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
     * Append [purpose] to the ordered list of purposes of the [session], persist it, and return the updated
     * session.
     *
     * Used by a purpose handler that, as it resolves, needs a follow-up purpose to run before the session
     * completes (e.g. the OAuth2 authorize purpose appending an MFA purpose).
     */
    suspend fun appendPurpose(
        session: OnGoingInteractiveFlowSession,
        purpose: InteractiveFlowPurpose
    ): OnGoingInteractiveFlowSession {
        val purposes = session.purposes + purpose
        sessionRepository.updatePurposes(
            id = session.id,
            purposes = purposes.map(InteractiveFlowPurpose::name)
        )
        return session.copy(purposes = purposes)
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
            purposes = session.purposes,
            initiatingPurpose = session.initiatingPurpose,
            flowId = session.flowId,
            errorDate = errorDate,
            errorDetailsId = error.detailsId,
            errorDescriptionId = error.descriptionId,
            errorValues = error.values,
            expirationDate = session.expirationDate
        )
    }

    /**
     * Mark the [session] as cancelled by the end-user, persist the cancellation date, and return the resulting
     * [CancelledInteractiveFlowSession].
     *
     * A [InteractiveFlowRedirectType.PLAIN] session with no cancellation URI has nowhere to hand the user back
     * to: this throws a recoverable [BusinessException] before transitioning, so the flow stays ongoing and the
     * caller gets a bad request rather than an un-buildable redirect. (An
     * [InteractiveFlowRedirectType.AUTHORIZATION_CODE] session always cancels back to the client redirect URI
     * with the OAuth2 `error=access_denied` response.)
     */
    suspend fun markAsCancelled(
        session: OnGoingInteractiveFlowSession
    ): CancelledInteractiveFlowSession {
        val redirectType = session.redirectType
            ?: throw internalBusinessExceptionOf("auth.interactive_flow_session.cancel.missing_redirect")
        if (redirectType == InteractiveFlowRedirectType.PLAIN && session.cancelRedirectUri == null) {
            throw recoverableBusinessExceptionOf(
                "auth.interactive_flow_session.cancel.no_cancel_target",
                "description.auth.interactive_flow_session.cancel.no_cancel_target"
            )
        }

        val cancelDate = LocalDateTime.now()
        sessionRepository.updateCancelDate(
            id = session.id,
            cancelDate = cancelDate
        )
        return CancelledInteractiveFlowSession(
            id = session.id,
            purposes = session.purposes,
            initiatingPurpose = session.initiatingPurpose,
            flowId = session.flowId,
            expirationDate = session.expirationDate,
            userId = session.userId,
            redirectType = redirectType,
            successRedirectUri = session.successRedirectUri,
            cancelRedirectUri = session.cancelRedirectUri,
            cancelDate = cancelDate,
        )
    }

    /**
     * Record [purpose] as completed on the ongoing [session] as the engine hands off to the next purpose,
     * persist the updated completed-purpose list, and return the still-[OnGoingInteractiveFlowSession].
     *
     * This is pure progress bookkeeping: it never transitions the session to completed — that is
     * [markAsCompleted], run by the engine once every purpose has resolved and its terminal effect applied.
     * Marking an already-completed [purpose] is a no-op on the list.
     */
    suspend fun markPurposeAsCompleted(
        session: OnGoingInteractiveFlowSession,
        purpose: InteractiveFlowPurpose
    ): OnGoingInteractiveFlowSession {
        val completedPurposes = (session.completedPurposes + purpose).distinct()
        sessionRepository.updateCompletedPurposes(
            id = session.id,
            completedPurposes = completedPurposes.map(InteractiveFlowPurpose::name)
        )
        return session.copy(completedPurposes = completedPurposes)
    }

    /**
     * Transition the ongoing [session] to completed: persist the completion date and return the resulting
     * [CompletedInteractiveFlowSession].
     *
     * Called by the engine once every purpose the session carries has resolved (and been marked complete via
     * [markPurposeAsCompleted]) and its terminal effect applied. The concern-specific completion invariants
     * (e.g. consent / grant for an OAuth2 session) are enforced by the matching purpose's terminal effect,
     * run before this transition.
     */
    suspend fun markAsCompleted(
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
            purposes = session.purposes,
            initiatingPurpose = session.initiatingPurpose,
            flowId = session.flowId,
            expirationDate = session.expirationDate,
            sessionDate = session.sessionDate,
            userId = userId,
            signedUp = session.signedUp,
            completedPurposes = session.completedPurposes,
            mfaPassedDate = session.mfaPassedDate,
            completeDate = completeDate,
            successRedirectUri = session.successRedirectUri
                ?: throw internalBusinessExceptionOf("auth.interactive_flow_session.complete.missing_redirect"),
            redirectType = session.redirectType
                ?: throw internalBusinessExceptionOf("auth.interactive_flow_session.complete.missing_redirect"),
            cancelRedirectUri = session.cancelRedirectUri,
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
            is CancelledInteractiveFlowSession -> session.userId?.let { userManager.findByIdOrNull(it) }
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
