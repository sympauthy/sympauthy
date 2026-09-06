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
     * signing them up is gone, and none of the five tables below refers to them.
     *
     * Nine tables hold a foreign key to `users`, and each has to be one of three things or this delete
     * breaks it. Four (`passwords`, `collected_claims`, `provider_user_info`, `totp_enrollments`) belong
     * to the account and go with it. The remaining five are guarded against here, and **they are not one
     * kind**: `interactive_flow_sessions` is itself collected, so an account only a session refers to
     * becomes deletable on its own once that session is gone, while `validation_codes`, `consents`,
     * `invitations` and `authentication_tokens` outlive every job there is — which is the split
     * [findRetained] answers the other side of.
     *
     * **A tenth table is a decision to make in this query and, if nothing collects it, in [findRetained]
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
     * Find at most [limit] accounts an abandoned sign-up left behind that **nothing will ever delete**: the
     * session signing them up is gone, and one of the four tables below still refers to them. Each is a
     * row that outlived the account it belongs to. Nothing here is deleted.
     *
     * The four are the referring tables no job removes. **`interactive_flow_sessions` is deliberately not
     * among them**, though [findCollectable] guards against it too: a session is collected, so an account
     * only a session refers to becomes deletable rather than staying here for good.
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
