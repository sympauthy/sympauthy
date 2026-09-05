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
import io.micronaut.transaction.annotation.Transactional
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
open class InteractiveFlowSessionManager(
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
        } catch (_: IllegalArgumentException) {
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
        val updated = sessionRepository.updateUserId(
            id = session.id,
            userId = userId,
            signedUp = signedUp,
            expectedVersion = session.version
        )
        if (updated == 0) throw concurrentModificationOf(session)
        return session.copy(
            userId = userId,
            signedUp = signedUp,
            version = session.version + 1
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
        val updated = sessionRepository.updateMfaPassedDate(
            id = session.id,
            mfaPassedDate = mfaPassedDate,
            expectedVersion = session.version
        )
        if (updated == 0) throw concurrentModificationOf(session)
        return session.copy(mfaPassedDate = mfaPassedDate, version = session.version + 1)
    }

    /**
     * Insert [purpose] into the ordered purpose list of the [session] immediately after [afterPurpose],
     * persist it, and return the updated session. Falls back to appending at the end when [afterPurpose] is
     * not present (which never happens for the engine's use — the resolving purpose is always in the list).
     *
     * Used by the engine when a purpose resolves and declares a follow-up purpose that must run before the
     * session completes (e.g. the re-authentication gate inserting an MFA challenge). Inserting **after** the
     * resolving purpose — rather than at the end — keeps the follow-up ahead of any later purpose the session
     * already carries, so e.g. a provider link never commits before its MFA challenge.
     */
    suspend fun insertPurposeAfter(
        session: OnGoingInteractiveFlowSession,
        purpose: InteractiveFlowPurpose,
        afterPurpose: InteractiveFlowPurpose
    ): OnGoingInteractiveFlowSession {
        val afterIndex = session.purposes.indexOf(afterPurpose)
        val insertAt = if (afterIndex >= 0) afterIndex + 1 else session.purposes.size
        val purposes = session.purposes.toMutableList().apply { add(insertAt, purpose) }.toList()
        val updated = sessionRepository.updatePurposes(
            id = session.id,
            purposes = purposes.map(InteractiveFlowPurpose::name).toTypedArray(),
            expectedVersion = session.version
        )
        if (updated == 0) throw concurrentModificationOf(session)
        return session.copy(purposes = purposes, version = session.version + 1)
    }

    /**
     * Set and save the error if it is non-recoverable to prevent further usage of the [session].
     * Do nothing if the [error] is recoverable.
     */
    @Transactional
    open suspend fun markAsFailedIfNotRecoverable(
        session: OnGoingInteractiveFlowSession,
        error: BusinessException
    ): InteractiveFlowSession {
        if (error.recoverable) return session

        val errorDate = LocalDateTime.now()
        // Two-step terminal write: a not-terminal guard then the derived error write, kept atomic by this
        // @Transactional so the row lock the guard takes serialises the two statements. It cannot be a
        // single versioned statement — the error_values JSON column round-trips only through Micronaut's
        // property-mapped serialization, not a raw-query parameter.
        //
        // The guard bumps the version only while the session is still ongoing. This request has usually
        // advanced the version itself before failing, so a version compare-and-swap on the remembered
        // (now-stale) version would wrongly swallow the write and leave the session ongoing; guarding on
        // "not already terminal" fails it correctly. A session a concurrent request already
        // completed/cancelled/failed yields 0 rows here and is left untouched — we must not overwrite that
        // winner (and a concurrent conflict never reaches this method: it is diverted in the controller).
        if (sessionRepository.failIfOngoing(session.id) == 1) {
            sessionRepository.updateError(
                id = session.id,
                errorDate = errorDate,
                errorDetailsId = error.detailsId,
                errorDescriptionId = error.descriptionId,
                errorValues = error.values
            )
        }
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
        val updated = sessionRepository.updateCancelDate(
            id = session.id,
            cancelDate = cancelDate,
            expectedVersion = session.version
        )
        if (updated == 0) throw concurrentModificationOf(session)
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
        val updated = sessionRepository.updateCompletedPurposes(
            id = session.id,
            completedPurposes = completedPurposes.map(InteractiveFlowPurpose::name).toTypedArray(),
            expectedVersion = session.version
        )
        if (updated == 0) throw concurrentModificationOf(session)
        return session.copy(completedPurposes = completedPurposes, version = session.version + 1)
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
        val updated = sessionRepository.updateCompleteDate(
            id = session.id,
            completeDate = completeDate,
            expectedVersion = session.version
        )
        if (updated == 0) throw concurrentModificationOf(session)
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

    /**
     * Re-read the [InteractiveFlowSession] with the given [id] from the database, or null if it no longer
     * exists. Unlike [verifyEncodedInternalState] this needs no signed state, so a caller holding a stale
     * in-memory session (e.g. after a concurrent-modification conflict) can refresh it to its current
     * persisted status and route by that.
     */
    suspend fun fetchByIdOrNull(id: UUID): InteractiveFlowSession? {
        return sessionRepository.findById(id)?.let(sessionMapper::toInteractiveFlowSession)
    }

    /**
     * Build the non-recoverable [BusinessException] thrown when a version-guarded update affects no
     * rows: the [session] snapshot the caller holds is stale because another request advanced the
     * session since it was read. Being non-recoverable, it is routed by the interactive-flow error
     * handling (see
     * [com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil.handleException]),
     * which reflects the session's current terminal status where possible and otherwise fails it.
     */
    private fun concurrentModificationOf(session: InteractiveFlowSession): BusinessException =
        businessExceptionOf(
            CONCURRENT_MODIFICATION_DETAILS_ID,
            "sessionId" to session.id.toString()
        )

    companion object {
        /**
         * Detail id of the non-recoverable exception raised when a version-guarded update loses its
         * compare-and-swap. The controller special-cases it (see
         * [com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil]).
         */
        const val CONCURRENT_MODIFICATION_DETAILS_ID = "auth.interactive_flow_session.concurrent_modification"

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
    val values: Map<String, String> = emptyMap()
) : VerifyEncodedStateResult()
