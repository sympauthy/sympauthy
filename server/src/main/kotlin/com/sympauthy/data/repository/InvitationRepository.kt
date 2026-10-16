package com.sympauthy.data.repository

import com.sympauthy.data.model.InvitationEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * Repository for [InvitationEntity].
 *
 * A status a statement below writes or compares is spelled as the business layer writes it into the column:
 * the name of an `InvitationStatus`.
 */
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
        SET status = 'CONSUMED', consumed_by_user_id = :consumedByUserId, consumed_at = :consumedAt
        WHERE id = :id AND status = 'PENDING'
        """
    )
    suspend fun consumeIfPending(id: UUID, consumedByUserId: UUID, consumedAt: LocalDateTime): Int

    /**
     * Revoke the invitation [id] **iff it is still pending and had not expired at [revokedAt]**, and answer 1
     * when it was and 0 when it was not.
     *
     * A compare-and-swap for the same reason as [consumeIfPending]: a sign-up may consume the invitation between
     * the read that found it pending and this write, and a plain write would revoke an invitation an account
     * already holds. The expiry is compared here because the column never holds it: a pending invitation past
     * its `expires_at` is one the business layer reads as expired.
     */
    @Query(
        """
        UPDATE invitations
        SET status = 'REVOKED', revoked_at = :revokedAt
        WHERE id = :id AND status = 'PENDING' AND expires_at >= :revokedAt
        """
    )
    suspend fun revokeIfPending(id: UUID, revokedAt: LocalDateTime): Int
}
