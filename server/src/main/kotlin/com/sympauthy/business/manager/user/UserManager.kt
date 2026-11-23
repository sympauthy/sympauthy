package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.claim.ClaimManager
import com.sympauthy.business.manager.claim.CollectedClaimManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.findCommittedClaimsMatching
import com.sympauthy.platform.lock.LockKey
import com.sympauthy.platform.lock.LockManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*

@Singleton
class UserManager(
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val userRepository: UserRepository,
    @Inject private val userMapper: UserMapper,
    @Inject private val lockManager: LockManager
) {

    @Inject
    private lateinit var claimValueMapper: ClaimValueMapper

    @Inject
    private lateinit var claimManager: ClaimManager

    /**
     * Find the committed end-user identified by [id]. Otherwise, return null.
     *
     * An account an interactive flow session is still signing up is not one, and is invisible here. The flow
     * that owns such an account reads it through [findByIdInSessionOrNull]. See
     * [com.sympauthy.data.model.SessionScoped].
     */
    suspend fun findByIdOrNull(id: UUID?): User? {
        return id?.let { userRepository.findByIdAndSessionIdIsNull(it) }
            ?.let(userMapper::toUser)
    }

    /**
     * Find the end-user identified by [id] as the interactive flow session [sessionId] sees it: the account
     * that session is still signing up, or any committed account. Otherwise, return null.
     *
     * A provisional account is seen only by the session creating it.
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
     * A caller enforcing uniqueness wants [findTakenIdentifierOrNull] instead —
     * `docs/design/identifier-claims.md` is why those are different reads.
     *
     * An account a session is still signing up never matches. See [com.sympauthy.data.model.SessionScoped].
     *
     * The values are business ones, and are compared folded rather than in the spelling they were given, so
     * an account is resolved by its address however the provider capitalised it.
     */
    suspend fun findByIdentifierClaims(claimValues: Map<String, Any>): User? {
        val foldedByClaimId = claimValues.mapNotNull { (claimId, value) ->
            claimValueMapper.toFoldedValue(value)?.let { claimId to it }
        }
        if (foldedByClaimId.isEmpty()) {
            return null
        }
        val matched = findCommittedClaimsFolding(foldedByClaimId)
            .groupBy(FoldedClaim::userId)
            .filterValues { rows -> foldedByClaimId.all { (claimId, _) -> rows.any { it.claimId == claimId } } }
        return userRepository.findByIdInListAndSessionIdIsNull(matched.keys.toList()).firstOrNull()
            ?.let(userMapper::toUser)
    }

    /**
     * Find the committed end-user holding [foldedByClaimId] under **any one** of the claims it names.
     * Otherwise, return null.
     *
     * This is how a single value somebody typed is resolved to an account: the caller has folded it once
     * per identifier claim, and a row of any of them holding it answers. A caller asking instead which
     * account holds *every* one of a set of values wants [findByIdentifierClaims], and one asking whether
     * a value is free wants [findTakenIdentifierOrNull] — `docs/design/identifier-claims.md` is why those are
     * three reads and not one.
     */
    suspend fun findByAnyIdentifierClaimValue(foldedByClaimId: Map<String, String>): User? {
        val matched = findCommittedClaimsFolding(foldedByClaimId.toList()).firstOrNull() ?: return null
        return findByIdOrNull(matched.userId)
    }

    /**
     * The committed rows whose claim is one of [foldedByClaim] and whose folded value is the one offered
     * for that claim, the hash having only narrowed the search.
     *
     * Why the hash is never trusted alone is `docs/design/identifier-claims.md`'s. A row whose claim this
     * deployment no longer declares cannot be checked and is therefore not one.
     */
    private suspend fun findCommittedClaimsFolding(
        foldedByClaim: Collection<Pair<String, String>>
    ): List<FoldedClaim> {
        val hashes = foldedByClaim.mapNotNull { (claimId, folded) ->
            claimValueMapper.toFoldedEqualityHash(folded)?.let { claimId to it }
        }.distinct()
        val expected = foldedByClaim.groupBy({ it.first }, { it.second })
        return collectedClaimRepository.findCommittedClaimsMatching(hashes).mapNotNull { row ->
            val claim = claimManager.findByIdOrNull(row.claim) ?: return@mapNotNull null
            val folded = claimValueMapper.toFoldedValueOfStored(row.value, claim.dataType)
                ?.takeIf { it in expected[row.claim].orEmpty() }
                ?: return@mapNotNull null
            FoldedClaim(userId = row.userId, claimId = row.claim, folded = folded)
        }
    }

    /**
     * A committed row that survived the re-check, carrying the folded value it was confirmed against so
     * that no caller derives it a second time.
     */
    private class FoldedClaim(val userId: UUID, val claimId: String, val folded: String)

    /**
     * Check that [userId] names an account this server has finished creating.
     *
     * Throws the non-recoverable `user.not_promoted` of the [checkPromoted] overload below, and
     * `user.not_found` when no account carries that id at all.
     *
     * It reads the row whether or not it is provisional, which is why the two codes are separate:
     * answering "no such account" for one that is merely unfinished would name the wrong failure,
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
     * caller's mistake and not a `500` — see `docs/standards/business-layer-code-standard.md`.
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
     * **This is the uniqueness of an identifier**, matched against every identifier claim in one query
     * rather than claim by claim. `docs/design/identifier-claims.md` is why it ranges over the whole set, and
     * why neither resolving read answers it.
     *
     * **[userId] is the account the values are being made to belong to, and every row it already holds is
     * exempt, under whichever identifier claim that row sits.** Only another account's row is a conflict,
     * and a caller with no account yet passes null and nothing is exempt.
     *
     * **It answers what is committed *now***, which is only worth asking under a lock over those values:
     * the account it has to exclude may commit between the read and whatever the caller does about it. It
     * raises nothing, leaving what to say about a value being taken to where it was being claimed. An
     * account a session is still signing up is invisible here — see `docs/design/provisional-user.md`.
     *
     * The values are folded ones — what [CollectedClaimManager.getFoldedValueOf] answers — and not the
     * ones `collected_claims` publishes.
     */
    suspend fun findTakenIdentifierOrNull(
        userId: UUID?,
        claimIds: List<String>,
        valuesByClaimId: Map<String, String>
    ): TakenIdentifier? {
        if (claimIds.isEmpty() || valuesByClaimId.isEmpty()) {
            return null
        }
        val offered = claimIds.flatMap { claimId -> valuesByClaimId.values.map { claimId to it } }.distinct()
        val committed = findCommittedClaimsFolding(offered)
        return valuesByClaimId.firstNotNullOfOrNull { (claimId, folded) ->
            committed.firstOrNull { it.folded == folded && it.userId != userId }
                ?.let { TakenIdentifier(claimId = claimId, userId = it.userId) }
        }
    }

    /**
     * Record that the person [userId] proved a credential at [authenticationDate], in a flow that is completing.
     *
     * An earlier date than the one they hold leaves theirs as it is, and so does an account still being signed
     * up. See `docs/design/security.md`.
     *
     * The account's row is written under [LockKey.User], taken before the write, so the row is never held by a
     * transaction still waiting for that lock. A caller already holding it keeps it, and one holding any other
     * names this one beside it, as [LockManager.withLock] requires.
     */
    suspend fun markAuthenticated(userId: UUID, authenticationDate: LocalDateTime) {
        lockManager.withLock(listOf(LockKey.User(userId))) {
            userRepository.advanceLastAuthenticationDate(userId, authenticationDate)
        }
    }

    /**
     * Record that a grant held for the person [userId] was refreshed at [refreshDate], on the terms of
     * [markAuthenticated].
     */
    suspend fun markRefreshed(userId: UUID, refreshDate: LocalDateTime) {
        lockManager.withLock(listOf(LockKey.User(userId))) {
            userRepository.advanceLastRefreshDate(userId, refreshDate)
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
    internal suspend fun createUser(sessionId: UUID?): User {
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
 * Both halves are named in the technical message of the refusal the caller raises, and neither in the
 * `description.` beside it, as `docs/standards/exception-code-standard.md` has it.
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
