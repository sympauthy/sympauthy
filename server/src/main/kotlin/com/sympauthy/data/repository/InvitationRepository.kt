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

    /**
     * Undo the consumption of every invitation taken by one of the accounts [userIds] that is still
     * provisional, and answer how many there were.
     *
     * The inverse of [consumeIfPending]. [userIds] must not be empty.
     *
     * **The reference is released whatever the status, and only a consumed invitation is made pending
     * again.** A row still naming a deleted account is a foreign key that breaks the collection; a
     * revoked or expired invitation must not come back as pending on the way out.
     *
     * The account is re-read because this table carries no session id of its own to re-assert. Why it is
     * re-read at all is in
     * [com.sympauthy.business.manager.user.ProvisionalAccountManager.deleteAbandoned].
     */
    @Query(
        """
        UPDATE invitations
        SET consumed_by_user_id = NULL,
            consumed_at = NULL,
            status = CASE WHEN status = :consumedStatus THEN :pendingStatus ELSE status END
        WHERE consumed_by_user_id IN (:userIds)
          AND EXISTS (
            SELECT 1 FROM users u WHERE u.id = invitations.consumed_by_user_id AND u.session_id IS NOT NULL
          )
        """
    )
    suspend fun unconsumeByUserIdInAndUserProvisional(
        userIds: List<UUID>,
        consumedStatus: String,
        pendingStatus: String
    ): Int

    suspend fun updateRevokedAt(
        @Id id: UUID,
        status: String,
        revokedAt: LocalDateTime
    )
}
