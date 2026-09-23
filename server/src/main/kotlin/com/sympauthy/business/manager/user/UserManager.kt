package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.CollectedClaimEntity
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
     * This resolves an identifier to the one account holding it, which is a different question from whether
     * a value is free: matching every entry is the point, and a caller enforcing uniqueness wants
     * [findTakenIdentifierOrNull] instead.
     *
     * An account a session is still signing up never matches. Two sign-ups may therefore hold the same
     * identifier at once; the collision is settled when the first of them promotes. See
     * [com.sympauthy.data.model.SessionScoped].
     *
     * The values are business ones, and are compared in the spelling `collected_claims` holds beside each
     * of them rather than in the one it was given, so an account is resolved by its address however the
     * provider capitalised it.
     */
    suspend fun findByIdentifierClaims(claimValues: Map<String, Any>): User? {
        val comparisonValues = claimValues.mapValues { (_, value) -> claimValueMapper.toComparisonValue(value) }
        val userIds = collectedClaimRepository.findUserIdsMatchingAllClaims(comparisonValues)
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
     * Return the first identifier claim in [valuesByClaimId] whose value a committed row already holds
     * under one of the identifier claims [claimIds], and the account holding it. Otherwise, return null.
     *
     * **This is the uniqueness of an identifier.** No database constraint says it: an end-user signs in
     * with any of the configured identifier claims, so a value belongs to one account across all of them
     * rather than within one column. That is why the offered values are matched against every identifier
     * claim in one query rather than claim by claim, and why a caller asking the question claim by claim —
     * or asking for a row matching *all* of the values it offers — would be asking a different one: the
     * account holding one of those values and not the others would answer nothing, and it owns the
     * identity just the same.
     *
     * **What it protects is that a value resolves to one account.** Two accounts holding one value make a
     * login resolve to either — including when neither is malformed on its own, which is the pair one
     * account holding `email = a` and `preferred_username = b` makes with another holding `email = b` and
     * `preferred_username = a`: typing `a` matches a row of each, and the owner of `a` is signed in
     * against the other account. Ranging over the whole set rather than over each claim is what excludes
     * that pair.
     *
     * **[userId] is the account the values are being made to belong to, and every row it already holds is
     * exempt, under whichever identifier claim that row sits.** A value it holds resolves to it either
     * way, so it takes nothing from anybody and there is no pair to exclude. Refusing its own row under a
     * second claim would refuse an account in that state every later rewrite of its own identifier and
     * every provider link, for an ambiguity it does not have. Only another account's row is a conflict,
     * and a caller with no account yet passes null and nothing is exempt.
     *
     * **It answers what is committed *now***, which is only worth asking under a lock over those values:
     * the account it has to exclude may commit between the read and whatever the caller does about it. It
     * answers what was taken and who has it, and raises nothing: what to say about a value being taken
     * belongs where it was being claimed, and the same loss is recoverable at one moment and not at the
     * next.
     *
     * An account a session is still signing up is invisible here, which is what lets two sign-ups hold one
     * value at a time — neither blocks the other, and the question is asked again when the first of them
     * promotes. See [com.sympauthy.data.model.SessionScoped] and `docs/provisional-user.md`.
     *
     * The values are the ones `collected_claims` compares on — [CollectedClaimEntity.comparisonValue],
     * which [CollectedClaimManager.getComparisonValueOf] spells — and not the ones it stores. Two
     * spellings of one value are one identity, so a caller offering the stored one would ask a question
     * every difference of case answers wrongly.
     */
    suspend fun findTakenIdentifierOrNull(
        userId: UUID?,
        claimIds: List<String>,
        valuesByClaimId: Map<String, String>
    ): TakenIdentifier? {
        if (claimIds.isEmpty() || valuesByClaimId.isEmpty()) {
            return null
        }
        val committed = collectedClaimRepository.findAnyClaimMatching(claimIds, valuesByClaimId.values.toList())
        return valuesByClaimId.firstNotNullOfOrNull { (claimId, comparisonValue) ->
            committed.firstOrNull { it.comparisonValue == comparisonValue && it.userId != userId }
                ?.let { TakenIdentifier(claimId = claimId, userId = it.userId) }
        }
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

/**
 * An identifier value a caller offered that a committed account already holds: what was taken, and who
 * has it.
 *
 * Both halves are named in the technical message of the refusal the caller raises — the one an operator
 * reads, which a deployment prints only by turning `features.print-details-in-error` on. Neither belongs
 * in the `description.` beside it: that one is shown to whoever tripped the refusal, and telling them
 * which account owns a value turns every check into an oracle over it. See
 * `docs/exception-code-standard.md`.
 */
data class TakenIdentifier(
    /**
     * The claim the caller offered the value under, which is never necessarily the one [userId] holds it
     * under — the rule ranges over every identifier claim, and only the offered half is something the
     * caller already knows.
     */
    val claimId: String,
    /** The committed account holding that value. */
    val userId: UUID
)

data class CreateOrAssociateResult(
    val created: Boolean,
    val user: User
)
