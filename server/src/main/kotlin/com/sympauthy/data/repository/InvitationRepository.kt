package com.sympauthy.data.repository

import com.sympauthy.data.model.InvitationEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface InvitationRepository : CoroutineCrudRepository<InvitationEntity, UUID> {

    suspend fun findByTokenLookupHash(tokenLookupHash: ByteArray): InvitationEntity?

    suspend fun findByAudienceId(audienceId: String): List<InvitationEntity>

    suspend fun findByCreatedById(createdById: String): List<InvitationEntity>

    suspend fun updateStatus(
        @Id id: UUID,
        status: String,
        consumedByUserId: UUID?,
        consumedAt: LocalDateTime?
    )

    suspend fun updateRevokedAt(
        @Id id: UUID,
        status: String,
        revokedAt: LocalDateTime
    )
}
