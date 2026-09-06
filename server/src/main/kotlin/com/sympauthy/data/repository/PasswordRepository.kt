package com.sympauthy.data.repository

import com.sympauthy.data.model.PasswordEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface PasswordRepository : CoroutineCrudRepository<PasswordEntity, UUID> {

    suspend fun findByUserId(userId: UUID): List<PasswordEntity>

    /**
     * Promote every password the account [userId] owns and the interactive flow session
     * [sessionId] created, making them permanent, and answer how many there were.
     */
    @Query("UPDATE passwords SET session_id = NULL WHERE user_id = :userId AND session_id = :sessionId")
    suspend fun clearSessionId(userId: UUID, sessionId: UUID): Int

    /**
     * Collect the passwords the accounts [userId] still hold provisionally, and answer how many there
     * were. Why the session id is re-asserted here is in
     * [com.sympauthy.business.manager.user.ProvisionalAccountManager.deleteAbandoned].
     */
    suspend fun deleteByUserIdInAndSessionIdIsNotNull(userId: List<UUID>): Int
}
