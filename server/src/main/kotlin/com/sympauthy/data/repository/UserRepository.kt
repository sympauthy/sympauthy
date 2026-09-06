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
     * Find at most [limit] accounts an abandoned sign-up left behind that can be collected: the session
     * signing them up is gone, and nothing else refers to them.
     *
     * Keyed on the session being **gone** rather than on the list of sessions any run expired, which makes
     * the sweep self-correcting: an account orphaned by an earlier failure is collected on the next run
     * rather than left forever. That is what makes the bound safe — what one run leaves the next takes —
     * and the bound is what keeps a backlog from becoming a single write holding locks on this table for
     * as long as it takes.
     *
     * **The bound is spent on accounts this run can actually delete.** An account one of the five tables
     * still refers to is answered by [findRetained] instead, and is never selected here, because a
     * retained account is retained for good: counted against the limit it would be re-read every run,
     * and enough of them would starve the sweep of the budget it needs to collect anything at all.
     *
     * The conditions are the rule this table lives under, written out. Nine tables hold a foreign key to
     * `users`, and each has to be one of three things or the delete that follows breaks it — and, being one
     * transaction, takes the whole run down with it, every quarter of an hour, for good. Four of them
     * (`passwords`, `collected_claims`, `provider_user_info`, `totp_enrollments`) belong to the account and
     * are deleted with it. The remaining five are the guards named below, and **they are not one kind**:
     *
     * - `interactive_flow_sessions` expires and is collected by
     *   [com.sympauthy.cron.CleanExpiredInteractiveFlowSessionCron]. An account only a session refers to is
     *   one this run is early for, not one anything is wrong with: the session goes, and a later run takes
     *   the account. It is guarded here and **not** reported by [findRetained].
     * - `validation_codes`, `consents`, `invitations` and `authentication_tokens` outlive any run. One of
     *   them pointing at an account no sign-up finished is a bug to find rather than a run to lose, so the
     *   account is left where it is and [findRetained] names it.
     *
     * **A tenth table is a decision to make in this query and, if it outlives a run, in [findRetained]
     * too.** `UserRepositoryTest` holds the two to that split, so adding a durable table here and not
     * there fails.
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
        LIMIT :limit
        """
    )
    suspend fun findCollectable(limit: Int): List<UserEntity>

    /**
     * Find at most [limit] accounts an abandoned sign-up left behind that **no run will ever collect**: the
     * session signing them up is gone, and one of the four tables below — none of which any cleanup
     * removes — still refers to them.
     *
     * Read for one reason: each one is a row that outlived the account it belongs to, which the sweep
     * reports rather than retains in silence. Nothing deletes what this returns.
     *
     * **`interactive_flow_sessions` is deliberately not among them**, though [findCollectable] guards
     * against it too. A session expires and is collected, so an account only a session refers to is
     * collected on a later run of its own accord — reporting it would name a run that was early as a bug
     * to find, every quarter of an hour, in the line that exists to name real ones.
     */
    @Query(
        """
        SELECT * FROM users
        WHERE session_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM interactive_flow_sessions s WHERE s.id = users.session_id)
          AND (
            EXISTS (SELECT 1 FROM validation_codes v WHERE v.user_id = users.id)
            OR EXISTS (SELECT 1 FROM consents c WHERE c.user_id = users.id)
            OR EXISTS (SELECT 1 FROM invitations i WHERE i.consumed_by_user_id = users.id)
            OR EXISTS (SELECT 1 FROM authentication_tokens t WHERE t.user_id = users.id)
          )
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
