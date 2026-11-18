package com.sympauthy.data.repository

import com.sympauthy.data.model.UserEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.*

interface UserRepository : CoroutineCrudRepository<UserEntity, UUID> {

    fun findBySessionIdIsNull(): Flow<UserEntity>

    suspend fun findByIdAndSessionIdIsNull(id: UUID): UserEntity?

    suspend fun findByIdInListAndSessionIdIsNull(id: List<UUID>): List<UserEntity>

    suspend fun findByIdAndSessionId(id: UUID, sessionId: UUID): UserEntity?

    /**
     * Find the user [id] as the interactive flow session [sessionId] sees it: the account that session is
     * still signing up, or any committed account. It takes the session, which is what exempts it from reading
     * committed rows only — see `docs/standards/business-layer-code-standard.md`.
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
     * Move the last authentication of the committed account [id] forward to [lastAuthenticationDate], and
     * answer 1 when it did. An account a session is still signing up, or one already holding a later date, is
     * left as it is. Why the comparison is the statement's is `docs/design/security.md`.
     */
    @Query(
        """
        UPDATE users SET last_authentication_date = :lastAuthenticationDate
        WHERE id = :id AND session_id IS NULL
        AND (last_authentication_date IS NULL OR last_authentication_date < :lastAuthenticationDate)
        """
    )
    suspend fun advanceLastAuthenticationDate(id: UUID, lastAuthenticationDate: LocalDateTime): Int

    /**
     * Move the last refresh of the account [id] to [lastRefreshDate], and answer 1 when it did, on the terms of
     * [advanceLastAuthenticationDate].
     */
    @Query(
        """
        UPDATE users SET last_refresh_date = :lastRefreshDate
        WHERE id = :id AND session_id IS NULL
        AND (last_refresh_date IS NULL OR last_refresh_date < :lastRefreshDate)
        """
    )
    suspend fun advanceLastRefreshDate(id: UUID, lastRefreshDate: LocalDateTime): Int

    /**
     * Find every account left provisional by a session that no longer exists, and that nothing else refers
     * to — the accounts an abandoned sign-up left behind, ready to be collected. Keyed on the session being
     * gone, so an account an earlier run failed to collect is found again by the next.
     *
     * This is where each table holding a foreign key to `users` is classified, as
     * `docs/design/provisional-user.md` requires. `passwords`, `collected_claims`, `provider_user_info` and
     * `totp_enrollments` belong to the account and are deleted with it. The tables named below are the
     * guard: an account any of them still refers to is skipped rather than deleted, because a row that
     * outlived its account is a bug to find, not a run to lose. `user_security_contexts` is never written
     * against a provisional account, so no row there can name one this query selects. **A new table
     * referencing `users` is a decision to make here.**
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
          AND NOT EXISTS (SELECT 1 FROM grants g WHERE g.user_id = users.id)
        """
    )
    suspend fun findAbandoned(): List<UserEntity>

    /**
     * Collect the accounts [id] that are still provisional, and answer how many there were.
     * Provisionality is re-asserted here rather than trusted from the read that selected them.
     */
    suspend fun deleteByIdInAndSessionIdIsNotNull(id: List<UUID>): Int
}
