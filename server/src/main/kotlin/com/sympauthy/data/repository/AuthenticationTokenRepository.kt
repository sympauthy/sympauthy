package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface AuthenticationTokenRepository : CoroutineCrudRepository<AuthenticationTokenEntity, UUID> {

    suspend fun updateRevokedAt(
        @Id id: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
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
    suspend fun updateRevokedAtByActorTokenId(
        actorTokenId: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int

    suspend fun updateRevokedAtByUserIdAndClientId(
        userId: UUID,
        clientId: String,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int

    suspend fun updateRevokedAtByUserId(
        userId: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID?
    ): Int

    /**
     * Delete the rows of the accounts [userIds] that are still provisional, and answer how many there
     * were. [userIds] must not be empty.
     *
     * The account is re-read because this table carries no session id of its own to re-assert. Why it is
     * re-read at all is in
     * [com.sympauthy.business.manager.user.ProvisionalAccountManager.deleteAbandoned].
     */
    @Query(
        """
        DELETE FROM authentication_tokens
        WHERE user_id IN (:userIds)
          AND EXISTS (SELECT 1 FROM users u WHERE u.id = authentication_tokens.user_id AND u.session_id IS NOT NULL)
        """
    )
    suspend fun deleteByUserIdInAndUserProvisional(userIds: List<UUID>): Int
}
