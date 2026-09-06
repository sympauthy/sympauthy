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
     * Promote the account [userId] that the interactive flow session [sessionId] created, making it
     * permanent, and answer 1 when it did.
     *
     * Keyed on the account as well as the session so a session that somehow wrote a second one cannot have
     * it promoted alongside — the uniqueness re-check runs against one account, and only that account may be
     * promoted by it. Any other stays provisional and is collected.
     */
    @Query("UPDATE users SET session_id = NULL WHERE id = :userId AND session_id = :sessionId")
    suspend fun clearSessionId(userId: UUID, sessionId: UUID): Int

    /**
     * Find at most [limit] accounts an abandoned sign-up left behind that can be deleted: the session
     * signing them up is gone, and neither of the two things that would block the delete refers to them.
     *
     * Nine tables hold a foreign key to `users`, and each has to be one of three things or this delete
     * breaks it. **Seven go with the account.** Four (`passwords`, `collected_claims`,
     * `provider_user_info`, `totp_enrollments`) belong to it outright; `validation_codes`, `consents` and
     * `authentication_tokens` are rows that should never have existed against an account no sign-up
     * finished — writing one refuses a provisional account — so the sweep collects them rather than
     * leaving the account behind them for good. Two are guarded against here instead:
     *
     * - `interactive_flow_sessions` is itself collected, so an account a session still refers to becomes
     *   deletable on its own once that session is gone. Waiting is the whole of the answer.
     * - `invitations` is not the account's to delete. An invitation is an artifact of whoever issued it,
     *   and one consumed by an account that never completed is the bug
     *   [findRetained] exists to report.
     */
    @Query(
        """
        SELECT * FROM users
        WHERE session_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM interactive_flow_sessions s WHERE s.id = users.session_id)
          AND NOT EXISTS (SELECT 1 FROM interactive_flow_sessions s WHERE s.user_id = users.id)
          AND NOT EXISTS (SELECT 1 FROM invitations i WHERE i.consumed_by_user_id = users.id)
        LIMIT :limit
        """
    )
    suspend fun findCollectable(limit: Int): List<UserEntity>

    /**
     * Find at most [limit] accounts an abandoned sign-up left behind that **nothing will ever delete**: the
     * session signing them up is gone, and an invitation records them as having consumed it.
     *
     * An invitation an account never completed should have stayed pending, so this is a row that outlived
     * the account it names — and it is the one referring row the sweep may not take with the account,
     * because the invitation belongs to whoever issued it rather than to the account that consumed it.
     * Nothing here is deleted.
     */
    @Query(
        """
        SELECT * FROM users
        WHERE session_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM interactive_flow_sessions s WHERE s.id = users.session_id)
          AND EXISTS (SELECT 1 FROM invitations i WHERE i.consumed_by_user_id = users.id)
        LIMIT :limit
        """
    )
    suspend fun findRetained(limit: Int): List<UserEntity>

    /**
     * Collect the accounts [id] that are still provisional, and answer how many there were. Why the
     * session id is re-asserted here is in
     * [com.sympauthy.business.manager.user.ProvisionalAccountManager.deleteAbandoned].
     */
    suspend fun deleteByIdInAndSessionIdIsNotNull(id: List<UUID>): Int
}
