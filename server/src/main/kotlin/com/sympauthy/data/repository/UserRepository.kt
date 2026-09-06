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

    /**
     * Find every account left provisional by a session that no longer exists, and that nothing else refers
     * to — the accounts an abandoned sign-up left behind, ready to be collected.
     *
     * Keyed on the session being **gone** rather than on the list of sessions this run expired, which makes
     * it self-correcting: an account orphaned by an earlier failure is collected on the next run rather than
     * left forever.
     *
     * The conditions are the rule this table lives under, written out. Nine tables hold a foreign key to
     * `users`, and each has to be one of three things or the delete that follows breaks it — and, being one
     * transaction, takes the whole run down with it, every quarter of an hour, for good. Four of them
     * (`passwords`, `collected_claims`, `provider_user_info`, `totp_enrollments`) belong to the account and
     * are deleted with it. The remaining five are the reasons named below: an account any of them still
     * refers to is skipped rather than deleted, because a row that outlived its account is a bug to find,
     * not a run to lose. **A tenth table is a decision to make here.**
     */
    @Query(
        """
        SELECT * FROM users
        WHERE session_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM interactive_flow_sessions s WHERE s.id = users.session_id)
          AND NOT EXISTS (SELECT 1 FROM interactive_flow_sessions s WHERE s.user_id = users.id)
          AND NOT EXISTS (SELECT 1 FROM validation_codes v WHERE v.user_id = users.id)
          AND NOT EXISTS (SELECT 1 FROM consents c WHERE c.user_id = users.id)
          AND NOT EXISTS (SELECT 1 FROM invitations i WHERE i.consumed_by_user_id = users.id)
          AND NOT EXISTS (SELECT 1 FROM authentication_tokens t WHERE t.user_id = users.id)
        """
    )
    suspend fun findAbandoned(): List<UserEntity>

    suspend fun deleteByIdIn(id: List<UUID>): Int
}
