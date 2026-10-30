package com.sympauthy.data.repository

import com.sympauthy.data.model.GrantEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * The condition every revocation adds to the grants it selects: a grant still live at the `revokedAt` it is
 * given. That is one not revoked yet, not past its own expiration date, and holding either a token not
 * expired or an authorization code not exchanged and not expired — a grant whose code is still to be
 * exchanged is one a client is about to be issued tokens under.
 *
 * A grant already revoked therefore keeps who revoked it first and when, and one that lapsed is left as it
 * lapsed rather than being recorded as revoked afterwards, so a revocation that counts answers how many
 * grants it took out of service. It is written once so that every revocation judges the same grants live.
 */
private const val LIVE_AT_REVOKED_AT = """
          AND revoked_at IS NULL AND (expiration_date IS NULL OR expiration_date >= :revokedAt)
          AND (
            EXISTS (
              SELECT 1 FROM authentication_tokens t
              WHERE t.grant_id = grants.id AND (t.expiration_date IS NULL OR t.expiration_date >= :revokedAt)
            )
            OR EXISTS (
              SELECT 1 FROM authorization_codes c
              WHERE c.grant_id = grants.id AND c.exchange_date IS NULL AND c.expiration_date >= :revokedAt
            )
          )
"""

/**
 * Every revocation reaches only a grant still live, as [LIVE_AT_REVOKED_AT] says.
 *
 * They are raw queries because the derived form can neither group an expiry, which a token without an
 * expiration date passes, nor reach the tokens and the codes of a grant.
 */
interface GrantRepository : CoroutineCrudRepository<GrantEntity, UUID> {

    /**
     * Every grant a revocation written at [revokedAt] would reach, which is what being live means.
     *
     * It reads the same condition the revocations below write under, so a grant read as live here is one a
     * revocation at that moment takes out of service, and one read otherwise is one it would leave as it is.
     */
    @Query(
        """
        SELECT * FROM grants
        WHERE TRUE
          $LIVE_AT_REVOKED_AT
        """
    )
    suspend fun findLiveAt(revokedAt: LocalDateTime): List<GrantEntity>

    /**
     * The grant [id] where a revocation written at [revokedAt] would reach it, and null where it would not or
     * there is no such grant.
     */
    @Query(
        """
        SELECT * FROM grants
        WHERE id = :id
          $LIVE_AT_REVOKED_AT
        """
    )
    suspend fun findLiveByIdAt(id: UUID, revokedAt: LocalDateTime): GrantEntity?

    @Query(
        """
        UPDATE grants
        SET revoked_at = :revokedAt, revoked_by = :revokedBy, revoked_by_id = :revokedById
        WHERE id = :id
          $LIVE_AT_REVOKED_AT
        """
    )
    suspend fun updateRevokedAt(
        id: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int

    /**
     * Revoke every grant a token exchange (RFC 8693) opened from a token of the grant identified by
     * [actorGrantId]. Returns the number of grants revoked.
     */
    @Query(
        """
        UPDATE grants
        SET revoked_at = :revokedAt, revoked_by = :revokedBy, revoked_by_id = :revokedById
        WHERE actor_grant_id = :actorGrantId
          $LIVE_AT_REVOKED_AT
        """
    )
    suspend fun updateRevokedAtByActorGrantId(
        actorGrantId: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int

    /**
     * Revoke every grant of [userId] opened for the audience identified by [audienceId], except the act-as
     * grants opened for it. Returns the number of grants revoked.
     */
    @Query(
        """
        UPDATE grants
        SET revoked_at = :revokedAt, revoked_by = :revokedBy, revoked_by_id = :revokedById
        WHERE user_id = :userId AND audience_id = :audienceId AND actor_grant_id IS NULL
          $LIVE_AT_REVOKED_AT
        """
    )
    suspend fun updateRevokedAtByUserIdAndAudienceIdAndActorGrantIdIsNull(
        userId: UUID,
        audienceId: String,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int

    /**
     * Delete every grant no token is left under and no authorization code names, and answer how many there
     * were.
     *
     * A code naming a grant is either still to be exchanged under it or, exchanged, what revokes it if it is
     * presented again, so the grant is kept as long as the code is, revoked or not; the foreign key from
     * `authorization_codes` refuses the delete otherwise.
     */
    @Query(
        """
        DELETE FROM grants
        WHERE NOT EXISTS (SELECT 1 FROM authentication_tokens t WHERE t.grant_id = grants.id)
          AND NOT EXISTS (SELECT 1 FROM authorization_codes c WHERE c.grant_id = grants.id)
        """
    )
    suspend fun deleteFinished(): Int
}
