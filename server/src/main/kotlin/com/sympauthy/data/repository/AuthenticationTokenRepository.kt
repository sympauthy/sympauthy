package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * Every revocation reaches only a token still live: one not revoked yet, and not expired at the `revokedAt`
 * it is given. A token already revoked keeps who revoked it first and when, and an expired one is left
 * having lapsed rather than being recorded as revoked afterwards, so a revocation that counts answers how
 * many tokens it took out of service.
 *
 * They are raw queries because the derived form cannot group the expiry, which a token without an
 * expiration date passes.
 */
interface AuthenticationTokenRepository : CoroutineCrudRepository<AuthenticationTokenEntity, UUID> {

    @Query(
        """
        UPDATE authentication_tokens
        SET revoked_at = :revokedAt, revoked_by = :revokedBy, revoked_by_id = :revokedById
        WHERE id = :id
          AND revoked_at IS NULL AND (expiration_date IS NULL OR expiration_date >= :revokedAt)
        """
    )
    suspend fun updateRevokedAt(
        id: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    )

    @Query(
        """
        UPDATE authentication_tokens
        SET revoked_at = :revokedAt, revoked_by = :revokedBy, revoked_by_id = :revokedById
        WHERE session_id = :sessionId
          AND revoked_at IS NULL AND (expiration_date IS NULL OR expiration_date >= :revokedAt)
        """
    )
    suspend fun updateRevokedAtBySessionId(
        sessionId: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    )

    /**
     * Revoke every act-as token (RFC 8693) derived from the token identified by [actorTokenId],
     * i.e. all tokens issued through token exchange with this token as their `subject_token`.
     * Returns the number of tokens revoked.
     */
    @Query(
        """
        UPDATE authentication_tokens
        SET revoked_at = :revokedAt, revoked_by = :revokedBy, revoked_by_id = :revokedById
        WHERE actor_token_id = :actorTokenId
          AND revoked_at IS NULL AND (expiration_date IS NULL OR expiration_date >= :revokedAt)
        """
    )
    suspend fun updateRevokedAtByActorTokenId(
        actorTokenId: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int

    /**
     * Revoke the tokens of [userId] issued to any of [clientIds], which must not be empty.
     * Returns the number of tokens revoked.
     */
    @Query(
        """
        UPDATE authentication_tokens
        SET revoked_at = :revokedAt, revoked_by = :revokedBy, revoked_by_id = :revokedById
        WHERE user_id = :userId AND client_id IN (:clientIds)
          AND revoked_at IS NULL AND (expiration_date IS NULL OR expiration_date >= :revokedAt)
        """
    )
    suspend fun updateRevokedAtByUserIdAndClientIdIn(
        userId: UUID,
        clientIds: List<String>,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int

    @Query(
        """
        UPDATE authentication_tokens
        SET revoked_at = :revokedAt, revoked_by = :revokedBy, revoked_by_id = :revokedById
        WHERE user_id = :userId
          AND revoked_at IS NULL AND (expiration_date IS NULL OR expiration_date >= :revokedAt)
        """
    )
    suspend fun updateRevokedAtByUserId(
        userId: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int
}
