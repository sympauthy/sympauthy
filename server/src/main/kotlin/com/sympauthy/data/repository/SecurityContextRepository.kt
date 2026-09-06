package com.sympauthy.data.repository

import com.sympauthy.data.model.SecurityContextEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface SecurityContextRepository : CoroutineCrudRepository<SecurityContextEntity, UUID> {

    suspend fun findByUserIdOrderByLastSeenDateDesc(userId: UUID): List<SecurityContextEntity>

    suspend fun findByUserIdAndFingerprint(userId: UUID, fingerprint: String): SecurityContextEntity?

    suspend fun findByIdIn(ids: List<UUID>): List<SecurityContextEntity>

    @Query(
        """
        SELECT * FROM security_contexts
        WHERE expiration_date < CURRENT_TIMESTAMP
        """
    )
    suspend fun findExpired(): List<SecurityContextEntity>

    suspend fun deleteByIdIn(ids: List<UUID>): Int

    suspend fun updateLastSeenDate(
        @Id id: UUID,
        lastSeenDate: LocalDateTime,
        observationCount: Int,
        expirationDate: LocalDateTime
    ): Int
}
