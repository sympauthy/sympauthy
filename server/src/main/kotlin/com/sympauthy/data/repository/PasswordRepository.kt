package com.sympauthy.data.repository

import com.sympauthy.data.model.PasswordEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface PasswordRepository : CoroutineCrudRepository<PasswordEntity, UUID> {

    suspend fun findByUserId(userId: UUID): List<PasswordEntity>

    /**
     * Promote every password the interactive flow session [sessionId] created, making it permanent, and
     * answer how many there were.
     */
    @Query("UPDATE passwords SET session_id = NULL WHERE session_id = :sessionId")
    suspend fun clearSessionId(sessionId: UUID): Int

    suspend fun deleteByUserIdIn(userId: List<UUID>): Int
}
