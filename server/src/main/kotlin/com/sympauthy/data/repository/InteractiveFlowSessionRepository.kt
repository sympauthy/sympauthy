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
 * parameter, so it cannot be expressed as a single versioned statement. It is instead guarded by a
 * scalar [bumpVersion] compare-and-swap paired with the derived [updateError] inside one transaction
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

    @Query(
        """
        SELECT * FROM interactive_flow_sessions
        WHERE expiration_date < CURRENT_TIMESTAMP
        """
    )
    suspend fun findExpired(): List<InteractiveFlowSessionEntity>

    /**
     * Version-guarded update of the ordered purpose list.
     * @return 1 if applied, 0 if [expectedVersion] was stale (see the optimistic-concurrency contract).
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
     * @return 1 if applied, 0 if [expectedVersion] was stale (see the optimistic-concurrency contract).
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
     * @return 1 if applied, 0 if [expectedVersion] was stale (see the optimistic-concurrency contract).
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
     * @return 1 if applied, 0 if [expectedVersion] was stale (see the optimistic-concurrency contract).
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
     * Version compare-and-swap used to guard a mutation whose columns cannot be bound as parameters
     * inside a raw versioned `@Query` — currently only the terminal error write (see [updateError]).
     * Increments the version iff the row still holds [expectedVersion]. Callers run it in the same
     * transaction as the accompanying partial update so the row lock it takes serialises concurrent
     * writers across both statements.
     * @return 1 if the swap succeeded, 0 if [expectedVersion] was stale.
     */
    @Query(
        """
        UPDATE interactive_flow_sessions
        SET version = version + 1
        WHERE id = :id AND version = :expectedVersion
        """
    )
    suspend fun bumpVersion(id: UUID, expectedVersion: Long): Int

    /**
     * Write the terminal error, failing the session. Unversioned on its own: it must be paired with a
     * preceding [bumpVersion] in the same transaction (see
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
     * @return 1 if applied, 0 if [expectedVersion] was stale (see the optimistic-concurrency contract).
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
     * @return 1 if applied, 0 if [expectedVersion] was stale (see the optimistic-concurrency contract).
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
