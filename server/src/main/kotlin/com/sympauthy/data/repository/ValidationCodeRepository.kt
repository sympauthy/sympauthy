package com.sympauthy.data.repository

import com.sympauthy.data.model.ValidationCodeEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface ValidationCodeRepository : CoroutineCrudRepository<ValidationCodeEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): List<ValidationCodeEntity>

    suspend fun findBySessionIdAndMedia(sessionId: UUID, media: String): List<ValidationCodeEntity>

    suspend fun deleteByIds(ids: List<UUID>)

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}
