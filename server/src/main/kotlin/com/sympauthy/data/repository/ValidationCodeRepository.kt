package com.sympauthy.data.repository

import com.sympauthy.data.model.ValidationCodeEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface ValidationCodeRepository : CoroutineCrudRepository<ValidationCodeEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): List<ValidationCodeEntity>

    suspend fun findBySessionIdAndMedia(sessionId: UUID, media: String): List<ValidationCodeEntity>

    suspend fun deleteByIds(ids: List<UUID>)

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int

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
        DELETE FROM validation_codes
        WHERE user_id IN (:userIds)
          AND EXISTS (SELECT 1 FROM users u WHERE u.id = validation_codes.user_id AND u.session_id IS NOT NULL)
        """
    )
    suspend fun deleteByUserIdInAndUserProvisional(userIds: List<UUID>): Int
}
