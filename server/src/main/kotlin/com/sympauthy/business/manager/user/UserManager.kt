package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.findAnyClaimMatching
import com.sympauthy.data.repository.findUserIdsMatchingAllClaims
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime.now
import java.util.*

@Singleton
open class UserManager(
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val userRepository: UserRepository,
    @Inject private val userMapper: UserMapper
) {

    @Inject
    private lateinit var claimValueMapper: ClaimValueMapper

    /**
     * Find the committed end-user identified by [id]. Otherwise, return null.
     *
     * An account an interactive flow session is still signing up is not one, and is invisible here: the ids
     * this takes arrive from outside — a path parameter, a token exchange target — and none of those callers
     * is entitled to an account this server has not finished creating. The flow that owns such an account
     * reads it through [findByIdInSessionOrNull]. See [com.sympauthy.data.model.SessionScoped].
     */
    suspend fun findByIdOrNull(id: UUID?): User? {
        return id?.let { userRepository.findByIdAndSessionIdIsNull(it) }
            ?.let(userMapper::toUser)
    }

    /**
     * Find the end-user identified by [id] as the interactive flow session [sessionId] sees it: the account
     * that session is still signing up, or any committed account. Otherwise, return null.
     *
     * The one reader entitled to a provisional account, since a session may only ever see the one it is
     * creating itself.
     */
    suspend fun findByIdInSessionOrNull(id: UUID?, sessionId: UUID): User? {
        return id?.let { userRepository.findByIdVisibleInSession(it, sessionId) }
            ?.let(userMapper::toUser)
    }

    /**
     * Find the end-user identified by [id]. Otherwise, throws an unrecoverable business exception.
     */
    suspend fun findById(id: UUID?): User {
        return findByIdOrNull(id) ?: throw businessExceptionOf(
            detailsId = "user.not_found",
            "userId" to "$id"
        )
    }

    /**
     * List the end-users identified by [ids], in the order the database returns them.
     * An id matching no row is absent from the result rather than null.
     */
    suspend fun listByIds(ids: List<UUID>): List<User> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return userRepository.findByIdInListAndSessionIdIsNull(ids).map(userMapper::toUser)
    }

    /**
     * Find a committed end-user whose collected claims match ALL entries in [claimValues].
     * Returns the first matching user, or null if none found.
     *
     * This is how an identifier is resolved to an account — at sign-in, when merging a provider identity, and
     * when refusing a duplicate sign-up — so an account a session is still signing up never matches. Two
     * sign-ups may therefore hold the same identifier at once; the collision is settled when the first of them
     * promotes. See [com.sympauthy.data.model.SessionScoped].
     */
    suspend fun findByIdentifierClaims(claimValues: Map<String, String>): User? {
        val entityClaimValues = claimValues.mapValues { entry -> claimValueMapper.toEntity(entry.value) }
        val userIds = collectedClaimRepository.findUserIdsMatchingAllClaims(entityClaimValues)
        return userRepository.findByIdInListAndSessionIdIsNull(userIds).firstOrNull()
            ?.let(userMapper::toUser)
    }

    /**
     * Check that [userId] names an account this server has finished creating.
     *
     * Throws the non-recoverable `user.not_promoted` of the [checkPromoted] overload below, and
     * `user.not_found` when no account carries that id at all.
     *
     * This is the one read here that does not exclude the provisional rows, and it is why the two codes are
     * separate: answering "no such account" for one that is merely unfinished would name the wrong failure,
     * and the caller that asks this question is asking precisely which of the two it is. The row goes through
     * [UserMapper] on the way, so a row this server can no longer read is refused here too, under the
     * mapper's own internal code.
     */
    suspend fun checkPromoted(userId: UUID) {
        val entity = userRepository.findById(userId) ?: throw businessExceptionOf(
            detailsId = "user.not_found",
            "userId" to "$userId"
        )
        // Mapped and the result dropped: the mapper is the only door into the model and the only place a row
        // is refused, so running it is how a row this server can no longer read fails here rather than
        // further along, under a code naming the property instead of whatever the caller tripped over next.
        checkPromoted(userMapper.toUser(entity))
    }

    /**
     * Check that [user] is an account this server has finished creating, for a caller that already holds it.
     * Throws a non-recoverable [BusinessException] `user.not_promoted` otherwise.
     *
     * This is a security control rather than an internal assertion, which is why it is a `400` naming the
     * caller's mistake and not a `500`. An account being signed up is one whose owner still decides what it
     * becomes: the session holding it can still abandon it, change the identifier it is claiming, or fail a
     * validation the sign-up has not reached. A caller that acts on it — mints it a token, enrols it a second
     * factor, attaches it a provider, records it a consent — hangs that on an account somebody else is still
     * authoring, and whoever owns that session inherits it when they promote. The id being unguessable is not
     * the defence; refusing to act on it is. See [com.sympauthy.data.model.SessionScoped].
     *
     * It reads the model rather than the row, so it costs nothing and cannot disagree with the account the
     * caller is about to act on.
     */
    fun checkPromoted(user: User) {
        if (user.sessionId != null) {
            throw businessExceptionOf(
                detailsId = "user.not_promoted",
                descriptionId = "description.user.not_promoted",
                "userId" to "${user.id}"
            )
        }
    }

    /**
     * Return true when a committed account already holds any of the [values] under any of the identifier
     * claims [claimIds].
     *
     * The uniqueness of an identifier is not a database constraint — an end-user may sign in with any of the
     * configured identifier claims, so a value has to be unique across all of them rather than within one
     * column, which is why the values are matched against every claim rather than claim by claim.
     *
     * Asked twice of one sign-up, against the same committed-only rows both times: once when the account is
     * created, and again when it is promoted, because an account created in the meantime would not have been
     * visible the first time. See [com.sympauthy.data.model.SessionScoped].
     */
    suspend fun isIdentifierValueTaken(claimIds: List<String>, values: List<String>): Boolean {
        return collectedClaimRepository.findAnyClaimMatching(claimIds, values).isNotEmpty()
    }

    /**
     * Create a new [User], provisional for the interactive flow session [sessionId] when it is signing the
     * account up, permanent from the start when it is null.
     *
     * Every row the account goes on to own takes its session id from the account rather than from the
     * request that writes it, so this is the single place a sign-up decides that the whole account is
     * provisional. See [com.sympauthy.data.model.SessionScoped].
     */
    @Transactional
    internal open suspend fun createUser(sessionId: UUID?): User {
        val entity = UserEntity(
            status = UserStatus.ENABLED.name,
            creationDate = now(),
            sessionId = sessionId
        )
        val savedEntity = userRepository.save(entity)
        return userMapper.toUser(savedEntity)
    }
}

data class CreateOrAssociateResult(
    val created: Boolean,
    val user: User
)
