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
     * were.
     *
     * A row here against an account no sign-up finished should not exist at all: writing one refuses a
     * provisional account through `UserManager.checkPromoted`. So the sweep collecting that account takes
     * this with it rather than leaving both, which is what
     * [com.sympauthy.business.manager.user.ProvisionalAccountManager.deleteAbandoned] is for.
     *
     * **The account is re-read rather than trusted**, for the reason the account's own delete re-asserts
     * its session id: a flow may promote it between the read that selected it and this statement. This
     * table has no session id of its own, so the predicate is spelled against `users`.
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
