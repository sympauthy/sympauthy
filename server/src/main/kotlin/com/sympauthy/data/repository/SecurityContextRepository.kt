package com.sympauthy.data.repository

import com.sympauthy.data.model.SecurityContextEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface SecurityContextRepository : CoroutineCrudRepository<SecurityContextEntity, UUID> {

    /**
     * The places [userId] was seen in before [excluding], most recent first, at most [limit] of them.
     *
     * The order is total — two places last seen at the same instant are separated by their identifier —
     * so the page a webhook is shown is the same page whichever row the database walks first.
     */
    @Query(
        """
        SELECT * FROM security_contexts
        WHERE user_id = :userId AND id <> :excluding
        ORDER BY last_seen_date DESC, id
        LIMIT :limit
        """
    )
    suspend fun findPastByUserId(userId: UUID, excluding: UUID, limit: Int): List<SecurityContextEntity>

    /**
     * The place [userId] was seen in under [fingerprint], the earliest where more than one exists.
     *
     * PostgreSQL refuses the second through a partial unique index; H2 cannot express one, so two
     * requests racing there can leave a duplicate, and a read that answered "more than one row" would
     * turn every review of that place into a failure rather than a decision.
     */
    suspend fun findFirstByUserIdAndFingerprintOrderByFirstSeenDate(
        userId: UUID,
        fingerprint: String
    ): SecurityContextEntity?

    suspend fun findByIdIn(ids: List<UUID>): List<SecurityContextEntity>

    /**
     * Delete every context whose retention has run out, and answer how many there were.
     *
     * It is one statement rather than a read and a delete by identifier: this is the largest table in
     * the schema, and a sweep that first materialised every expired row would hold them all — addresses
     * and user agents — in memory, and bind one parameter per row in the delete that followed.
     */
    @Query(
        """
        DELETE FROM security_contexts
        WHERE expiration_date < CURRENT_TIMESTAMP
        """
    )
    suspend fun deleteExpired(): Int

    suspend fun deleteByIdIn(ids: List<UUID>): Int

    suspend fun deleteByUserIdIn(userIds: List<UUID>): Int

    suspend fun updateLastSeenDate(
        @Id id: UUID,
        lastSeenDate: LocalDateTime,
        observationCount: Int,
        expirationDate: LocalDateTime
    ): Int

    suspend fun updateUserId(
        @Id id: UUID,
        userId: UUID,
        expirationDate: LocalDateTime
    ): Int

    suspend fun updateLastDecision(
        @Id id: UUID,
        lastDecision: String,
        lastDecisionDate: LocalDateTime
    ): Int

    suspend fun updateFirstSeenDate(
        @Id id: UUID,
        firstSeenDate: LocalDateTime,
        lastSeenDate: LocalDateTime,
        observationCount: Int,
        expirationDate: LocalDateTime
    ): Int
}
