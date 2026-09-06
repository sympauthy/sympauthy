package com.sympauthy.data.repository

import com.sympauthy.data.model.UserEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import kotlinx.coroutines.flow.Flow
import java.util.*

interface UserRepository : CoroutineCrudRepository<UserEntity, UUID> {

    fun findByStatusAndSessionIdIsNull(status: String): Flow<UserEntity>

    fun findBySessionIdIsNull(): Flow<UserEntity>

    suspend fun findByIdAndSessionIdIsNull(id: UUID): UserEntity?

    suspend fun findByIdInListAndSessionIdIsNull(id: List<UUID>): List<UserEntity>

    suspend fun findByIdAndSessionId(id: UUID, sessionId: UUID): UserEntity?

    /**
     * Find the user [id] as the interactive flow session [sessionId] sees it: the account that session is
     * still signing up, or any committed account.
     *
     * The one read that may return a provisional row, for the flow that owns it. Every other reader of this
     * table sees committed rows only — see [com.sympauthy.data.model.SessionScoped].
     */
    @Query(
        """
        SELECT * FROM users
        WHERE id = :id AND (session_id IS NULL OR session_id = :sessionId)
        """
    )
    suspend fun findByIdVisibleInSession(id: UUID, sessionId: UUID): UserEntity?

    /**
     * Promote every account the interactive flow session [sessionId] created, making it permanent, and
     * answer how many there were.
     */
    @Query("UPDATE users SET session_id = NULL WHERE session_id = :sessionId")
    suspend fun clearSessionId(sessionId: UUID): Int
}
