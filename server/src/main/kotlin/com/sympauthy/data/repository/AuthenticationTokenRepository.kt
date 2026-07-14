package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.data.annotation.Id
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

    suspend fun updateRevokedAtByAuthorizeAttemptId(
        authorizeAttemptId: UUID,
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
}
