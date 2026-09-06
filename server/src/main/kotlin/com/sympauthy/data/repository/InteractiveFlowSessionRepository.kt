package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * Repository for [InteractiveFlowSessionEntity], the parent row of an interactive flow session.
 *
 * ## Optimistic-concurrency contract
 *
 * [InteractiveFlowSessionEntity.version] is a monotonic counter that guards every lifecycle
 * mutation against lost updates — the case where a replayed or double-submitted step request
 * overwrites state a more recent request already advanced. Each `updateXxx` method below is a single
 * compare-and-swap statement of the form:
 *
 * ```sql
 * UPDATE interactive_flow_sessions
 * SET <changed columns>, version = version + 1
 * WHERE id = :id AND version = :expectedVersion
 * ```
 *
 * The caller passes `expectedVersion` = the version of the session it last read, and inspects the
 * returned affected-row count:
 * - `1` — the row still held `expectedVersion`: the update was applied and the version incremented.
 * - `0` — the row no longer holds `expectedVersion`: another request advanced (or terminated) the
 *   session since it was read, so the caller's in-memory snapshot is stale and the update was a
 *   no-op. The caller must treat this as a concurrent-modification conflict. See
 *   [com.sympauthy.business.manager.flow.InteractiveFlowSessionManager], which fails the session
 *   (routing the end-user to the error page) on a lost swap.
 *
 * Each statement is atomic on its own: the row lock a matching `UPDATE` takes serialises concurrent
 * writers, so exactly one can observe a given `expectedVersion`. No surrounding transaction is
 * required.
 *
 * The lone exception is the terminal error write: its `error_values` `json` column round-trips
 * correctly only through Micronaut's property-mapped serialization, not through a raw-query
 * parameter, so it cannot be expressed as a single versioned statement. It is instead a scalar
 * [failIfOngoing] guard (which bumps the version only while the session is still ongoing) paired with
 * the derived [updateError] inside one transaction
 * (see [com.sympauthy.business.manager.flow.InteractiveFlowSessionManager.markAsFailedIfNotRecoverable]).
 *
 * `version` is deliberately a plain column rather than a Micronaut Data `@Version` property:
 * `@Version` optimistic locking only engages on full-entity `update(entity)` / `delete(entity)`,
 * whereas the session is only ever mutated through these query-based partial updates, so the check
 * and the increment are expressed explicitly in SQL here instead.
 */
interface InteractiveFlowSessionRepository : CoroutineCrudRepository<InteractiveFlowSessionEntity, UUID> {

    @Query(
        """
        SELECT * FROM interactive_flow_sessions AS s
        JOIN authorization_codes AS ac ON s.id = ac.session_id
        WHERE ac.code = :code
        """
    )
    suspend fun findByCode(code: String): InteractiveFlowSessionEntity?

    /**
     * Find at most [limit] sessions past their expiration date.
     *
     * Bounded because the caller deletes each of them from eight tables inside one transaction, and an
     * `IN` list is one bind parameter per id: an unbounded backlog is a write holding locks for as long
     * as it takes, and past 65535 ids a statement PostgreSQL refuses outright. A run that fills the
     * limit leaves the rest to the next one. See
     * [com.sympauthy.business.manager.flow.InteractiveFlowSessionCleaner].
     */
    @Query(
        """
        SELECT * FROM interactive_flow_sessions
        WHERE expiration_date < CURRENT_TIMESTAMP
        LIMIT :limit
        """
    )
    suspend fun findExpired(limit: Int): List<InteractiveFlowSessionEntity>

    /**
     * Version-guarded update of the ordered purpose list.
     */
    @Query(
        """
        UPDATE interactive_flow_sessions
        SET purposes = :purposes, version = version + 1
        WHERE id = :id AND version = :expectedVersion
        """
    )
    suspend fun updatePurposes(
        id: UUID,
        @TypeDef(type = DataType.STRING_ARRAY) purposes: Array<String>,
        expectedVersion: Long
    ): Int

    /**
     * Version-guarded update of the authenticated user id and sign-up flag.
     */
    @Query(
        """
        UPDATE interactive_flow_sessions
        SET user_id = :userId, signed_up = :signedUp, version = version + 1
        WHERE id = :id AND version = :expectedVersion
        """
    )
    suspend fun updateUserId(id: UUID, userId: UUID, signedUp: Boolean, expectedVersion: Long): Int

    /**
     * Version-guarded update of the MFA-passed date.
     */
    @Query(
        """
        UPDATE interactive_flow_sessions
        SET mfa_passed_date = :mfaPassedDate, version = version + 1
        WHERE id = :id AND version = :expectedVersion
        """
    )
    suspend fun updateMfaPassedDate(id: UUID, mfaPassedDate: LocalDateTime, expectedVersion: Long): Int

    /**
     * Version-guarded update of the completed-purpose list.
     */
    @Query(
        """
        UPDATE interactive_flow_sessions
        SET completed_purposes = :completedPurposes, version = version + 1
        WHERE id = :id AND version = :expectedVersion
        """
    )
    suspend fun updateCompletedPurposes(
        id: UUID,
        @TypeDef(type = DataType.STRING_ARRAY) completedPurposes: Array<String>,
        expectedVersion: Long
    ): Int

    /**
     * Fail-guard for the terminal error write: bump the version **iff the session is still ongoing**
     * (no completion, cancellation or error date yet).
     *
     * Used instead of a version compare-and-swap because the failing request has usually advanced the
     * session's version itself before hitting the non-recoverable error, so the version it remembers is
     * stale — yet the session must still be failed. Guarding on "not already terminal" instead lets that
     * self-advanced session be failed while still refusing to overwrite a session a concurrent request
     * already drove to a terminal state (a concurrent conflict is diverted before this point; see
     * [com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil]).
     *
     * Paired with the derived [updateError] in one transaction so the row lock it takes serialises the
     * two statements. It answers 1 when the session was still ongoing and is now failing, and 0 when it had
     * already reached a terminal state and was left as it was.
     */
    @Query(
        """
        UPDATE interactive_flow_sessions
        SET version = version + 1
        WHERE id = :id AND complete_date IS NULL AND cancel_date IS NULL AND error_date IS NULL
        """
    )
    suspend fun failIfOngoing(id: UUID): Int

    /**
     * Write the terminal error, failing the session. Unguarded on its own: it must be paired with a
     * preceding [failIfOngoing] in the same transaction (see
     * [com.sympauthy.business.manager.flow.InteractiveFlowSessionManager.markAsFailedIfNotRecoverable]).
     * Kept as a derived method so `error_values` is serialized to JSON by Micronaut's property mapping,
     * which a raw-query parameter does not do.
     */
    suspend fun updateError(
        @Id id: UUID,
        errorDate: LocalDateTime?,
        errorDetailsId: String?,
        errorDescriptionId: String?,
        errorValues: Map<String, String>?
    )

    /**
     * Version-guarded write of the completion date, completing the session.
     */
    @Query(
        """
        UPDATE interactive_flow_sessions
        SET complete_date = :completeDate, version = version + 1
        WHERE id = :id AND version = :expectedVersion
        """
    )
    suspend fun updateCompleteDate(id: UUID, completeDate: LocalDateTime?, expectedVersion: Long): Int

    /**
     * Version-guarded write of the cancellation date, cancelling the session.
     */
    @Query(
        """
        UPDATE interactive_flow_sessions
        SET cancel_date = :cancelDate, version = version + 1
        WHERE id = :id AND version = :expectedVersion
        """
    )
    suspend fun updateCancelDate(id: UUID, cancelDate: LocalDateTime, expectedVersion: Long): Int

    suspend fun deleteByIds(ids: List<UUID>): Int
}
