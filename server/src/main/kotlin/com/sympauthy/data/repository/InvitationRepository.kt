package com.sympauthy.data.repository

import com.sympauthy.data.model.InvitationEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface InvitationRepository : CoroutineCrudRepository<InvitationEntity, UUID> {

    suspend fun findByTokenLookupHash(tokenLookupHash: ByteArray): InvitationEntity?

    suspend fun findByAudienceId(audienceId: String): List<InvitationEntity>

    suspend fun findByCreatedById(createdById: String): List<InvitationEntity>

    /**
     * Consume the invitation [id] on behalf of [consumedByUserId] **iff it is still pending**, and answer 1
     * when it was and 0 when someone else had already taken it.
     *
     * A compare-and-swap rather than a plain write because two sign-ups may hold the same invitation at once:
     * both were let through while provisional, and this is where the first of them to complete wins.
     */
    @Query(
        """
        UPDATE invitations
        SET status = :status, consumed_by_user_id = :consumedByUserId, consumed_at = :consumedAt
        WHERE id = :id AND status = :pendingStatus
        """
    )
    suspend fun consumeIfPending(
        id: UUID,
        status: String,
        pendingStatus: String,
        consumedByUserId: UUID,
        consumedAt: LocalDateTime
    ): Int

    suspend fun updateRevokedAt(
        @Id id: UUID,
        status: String,
        revokedAt: LocalDateTime
    )
}
