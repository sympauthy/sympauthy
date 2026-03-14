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
