package com.sympauthy.data.repository

import com.sympauthy.data.model.ConsentEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface ConsentRepository : CoroutineCrudRepository<ConsentEntity, UUID> {

    suspend fun findByUserIdAndAudienceIdAndRevokedAtIsNull(userId: UUID, audienceId: String): ConsentEntity?

    suspend fun findByUserIdAndClientIdAndRevokedAtIsNull(userId: UUID, clientId: String): ConsentEntity?

    suspend fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<ConsentEntity>

    suspend fun findByUserId(userId: UUID): List<ConsentEntity>

    suspend fun findByClientId(clientId: String): List<ConsentEntity>

    suspend fun findByClientIdAndRevokedAtIsNull(clientId: String): List<ConsentEntity>

    suspend fun findByAudienceIdAndRevokedAtIsNull(audienceId: String): List<ConsentEntity>

    suspend fun updateRevokedAt(
        @Id id: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID
    ): Int
}
