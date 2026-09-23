package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.CollectedClaimUpdateMapper
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import java.util.*

/**
 * Reads and writes the claims an account has had collected, restricted by neither consent nor scope.
 *
 * [ConsentAwareCollectedClaimManager] is what applies both, and what a read answering an end-user or a
 * client goes through instead; this one is for a question consent does not decide.
 */
@Singleton
open class CollectedClaimManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val userManager: UserManager,
    @Inject private val lockManager: LockManager,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val collectedClaimMapper: CollectedClaimMapper,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper,
    @Inject private val claimValueValidator: ClaimValueValidator,
    @Inject private val claimValueMapper: ClaimValueMapper
) {

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId].
     */
    suspend fun findByUserId(userId: UUID): List<CollectedClaim> {
        return collectedClaimRepository.findByUserId(userId)
            .asSequence()
            .mapNotNull(collectedClaimMapper::toCollectedClaim)
            .toList()
    }

    /**
     * Return the list of [CollectedClaim] for the given [claims] collected from the user identified by [userId].
     */
    suspend fun findByUserIdAndClaims(userId: UUID, claims: List<Claim>): List<CollectedClaim> {
        val claimIds = claims.map { it.id }
        return collectedClaimRepository.findByUserIdAndClaimInList(userId, claimIds)
            .mapNotNull(collectedClaimMapper::toCollectedClaim)
    }

    /**
     * Return the list of [CollectedClaim] the audience identified by [audienceId] has, collected from the user
     * identified by [userId] — those restricted to that audience and those restricted to none.
     *
     * Note: this one is restricted by audience, which the reads above are not. It is what a caller acting
     * for one audience reads where consent does not apply to the question it is asking, a rule deciding a
     * grant being the case.
     */
    suspend fun findByUserIdAndAudience(userId: UUID, audienceId: String): List<CollectedClaim> {
        return findByUserIdAndClaims(
            userId = userId,
            claims = claimManager.listAllClaims().filter { it.belongsToAudience(audienceId) }
        )
    }

    /**
     * Return the list of [CollectedClaim] for the identifier claims collected from the user identified by [userId].
     */
    suspend fun findIdentifierByUserId(userId: UUID): List<CollectedClaim> {
        return findByUserIdAndClaims(userId, claimManager.listIdentifierClaims())
    }

    /**
     * Return the identifier claims collected from the users identified by [userIds], for all of them at once.
     */
    suspend fun listIdentifierByUserIds(userIds: List<UUID>): List<CollectedClaim> {
        if (userIds.isEmpty()) {
            return emptyList()
        }
        val claimIds = claimManager.listIdentifierClaims().map(Claim::id)
        if (claimIds.isEmpty()) {
            return emptyList()
        }
        return collectedClaimRepository.findByUserIdInListAndClaimInList(userIds, claimIds)
            .mapNotNull(collectedClaimMapper::toCollectedClaim)
    }

    /**
     * Return true if all [Claim] that have been marked as [Claim.required] have been collected from the end-user.
     */
    fun areAllRequiredClaimCollected(collectedClaims: List<CollectedClaim>): Boolean {
        val requiredClaims = claimManager.listRequiredClaims()
        if (requiredClaims.isEmpty()) {
            return true
        }
        val missingRequiredClaims = collectedClaims.fold(requiredClaims.toMutableSet()) { acc, claim ->
            acc.remove(claim.claim)
            acc
        }
        return missingRequiredClaims.isEmpty()
    }

    /**
     * Update the claims collected for the [user] and return all the claims collected for the user.
     * All [updates] will be applied without any scope restriction.
     *
     * Throws the `user.claims.identifier_taken` of [applyUpdates] under the same conditions.
     *
     * For consent-restricted updates, use [ConsentAwareCollectedClaimManager.update] instead.
     */
    @Transactional
    open suspend fun update(
        user: User,
        updates: List<CollectedClaimUpdate>
    ): List<CollectedClaim> {
        return applyUpdates(user, updates)
    }

    /**
     * Update the claims collected for the [user] and return all the claims collected for the user
     * (including one previously collected but not updated by the call to this method).
     *
     * Throws a non-recoverable [BusinessException] `user.claims.identifier_taken` when one of the
     * [applicableUpdates] would give a **committed** [user] an identifier value another committed account
     * already holds. **Uniqueness on an identifier value is enforced here and nowhere below**: an account
     * signing in with any of the configured identifier claims means a value has to be unique across all of
     * them rather than within one column, so no constraint expresses it and a write that goes around this
     * method is a write nothing refuses. Two accounts left holding one value make
     * [com.sympauthy.data.repository.findAnyClaimMatching] match twice, and every later sign-in with that
     * value fails for both of them.
     *
     * **A write to a provisional account is checked by nothing here, deliberately.** Its identifier is not
     * one yet: two sign-ups may hold one value at a time, and the collision is settled under the same key by
     * [ProvisionalAccountManager.promote]. Checking here would answer a question that account is not yet
     * asking, and — since the promotion and the sign-up both already lock and check — would only add a
     * second `withLock` to transactions that already hold one. See [com.sympauthy.data.model.SessionScoped]
     * and `docs/provisional-user.md`.
     *
     * **The check is serialised by the values being written**, held over the check and the writes both, and
     * the competitor it has to exclude is whichever of the promotion and another write commits first. The
     * keys name the value as `collected_claims` spells it, which is what both sides compare on. See
     * [LockKey.IdentifierValue] and `docs/locking-standard.md`.
     */
    @Transactional
    open suspend fun applyUpdates(
        user: User,
        applicableUpdates: List<CollectedClaimUpdate>
    ): List<CollectedClaim> {
        if (user.sessionId != null) {
            return writeUpdates(user, applicableUpdates)
        }
        val identifierValues = getIdentifierFoldedValuesIn(applicableUpdates)
        if (identifierValues.isEmpty()) {
            return writeUpdates(user, applicableUpdates)
        }
        val keys = identifierValues.values.map(LockKey::IdentifierValue).toTypedArray()
        return lockManager.withLock(*keys) {
            checkIdentifierValuesFree(user, identifierValues)
            writeUpdates(user, applicableUpdates)
        }
    }

    /**
     * The identifier claim values [updates] would write, by the claim writing them, folded the way
     * `collected_claims` compares them. An update clearing a claim is not one: it takes no value from
     * anybody.
     *
     * Public because it is the one spelling of that map: asking whether these values are taken means asking
     * in the spelling the rows compare on, and writing it out again elsewhere is a second definition of
     * which updates count and how their values are folded, with the two to be changed together. It answers
     * for an update, whose value the validator already cleaned, by folding exactly what the write will
     * fold; a caller holding a value that has not been through the validator asks [getFoldedValueOf],
     * which cleans it first and answers the same spelling.
     */
    fun getIdentifierFoldedValuesIn(updates: List<CollectedClaimUpdate>): Map<String, String> {
        val identifierClaims = claimManager.listIdentifierClaims().toSet()
        if (identifierClaims.isEmpty()) {
            return emptyMap()
        }
        return updates
            .filter { it.claim in identifierClaims }
            .mapNotNull { update ->
                collectedClaimUpdateMapper.toFoldedValue(update.value)?.let { update.claim.id to it }
            }
            .toMap()
    }

    /**
     * The folded value [claim] compares [value] under, or null where [claim] could hold no such value at
     * all. It answers equality under folding and nothing else.
     *
     * Cleaning and then folding is one step, and this is where it is written for a caller holding a value
     * rather than a row: a caller that folded without cleaning would ask for a spelling no write ever
     * produced, and one that cleaned under a claim other than the one it is looking under would ask a
     * number claim for what a string claim made of the value. Answering null for a value the claim would
     * refuse is the right answer rather than a failure — no row of that claim holds one either.
     *
     * It agrees with what the write folds before hashing it into
     * [CollectedClaimEntity.foldedEqualityHash]; the two spell one rule and change together.
     */
    fun getFoldedValueOf(claim: Claim, value: Any): String? {
        return claimValueValidator.cleanValueForClaimOrNull(claim, value)
            ?.let(claimValueMapper::toFoldedValue)
    }

    /**
     * The folded value each configured identifier claim compares [value] under, by the claim comparing it.
     * A claim that could hold no such value at all is absent rather than present with nothing.
     *
     * This is how one typed value is offered to the whole set: each claim is asked in the spelling it
     * would have folded the value to, because a claim only matches a row that went through its own
     * cleaning first.
     */
    fun getIdentifierFoldedValuesOf(value: Any): Map<String, String> {
        return claimManager.listIdentifierClaims().mapNotNull { claim ->
            getFoldedValueOf(claim, value)?.let { claim.id to it }
        }.toMap()
    }

    /**
     * Throw `user.claims.identifier_taken`, naming the claim that lost, when a committed row already holds
     * one of the [identifierValues] — [UserManager.findTakenIdentifierOrNull] is the rule, including
     * why a row [user] already holds is never one of them, under whichever identifier claim it sits.
     *
     * It answers what is committed *now*, which is only worth asking under the lock its caller holds over
     * those values.
     */
    internal suspend fun checkIdentifierValuesFree(user: User, identifierValues: Map<String, String>) {
        val claimIds = claimManager.listIdentifierClaims().map(Claim::id)
        val taken = userManager.findTakenIdentifierOrNull(user.id, claimIds, identifierValues) ?: return
        throw businessExceptionOf(
            detailsId = "user.claims.identifier_taken",
            descriptionId = "description.user.claims.identifier_taken",
            "claim" to taken.claimId,
            "userId" to "${taken.userId}"
        )
    }

    internal suspend fun writeUpdates(
        user: User,
        applicableUpdates: List<CollectedClaimUpdate>
    ): List<CollectedClaim> = coroutineScope {
        val existingEntityByClaimMap = collectedClaimRepository.findByUserId(user.id)
            .associateBy { it.claim }
            .toMutableMap()

        val deferredDeletedEntities = async {
            deleteExistingClaimsUpdatedToNull(existingEntityByClaimMap, applicableUpdates)
        }
        val deferredCreatedEntities = async {
            createMissingClaims(user, existingEntityByClaimMap, applicableUpdates)
        }
        val updatedEntities = updateExistingClaims(existingEntityByClaimMap, applicableUpdates)

        val updatedAndDeletedClaims = (updatedEntities + deferredDeletedEntities.await())
            .map(CollectedClaimEntity::claim)
            .toSet()
        val nonUpdatedOrDeletedEntities = existingEntityByClaimMap.values
            .filter { !updatedAndDeletedClaims.contains(it.claim) }

        (deferredCreatedEntities.await() + updatedEntities + nonUpdatedOrDeletedEntities)
            .mapNotNull(collectedClaimMapper::toCollectedClaim)
    }

    internal suspend fun deleteExistingClaimsUpdatedToNull(
        existingEntityByClaimMap: Map<String, CollectedClaimEntity>,
        applicableUpdates: List<CollectedClaimUpdate>
    ): List<CollectedClaimEntity> {
        val entitiesToDelete = applicableUpdates
            .filter { it.value == null }
            .mapNotNull { existingEntityByClaimMap[it.claim.id] }
        collectedClaimRepository.deleteAll(entitiesToDelete)
        return entitiesToDelete
    }

    internal suspend fun updateExistingClaims(
        existingEntityByClaimMap: Map<String, CollectedClaimEntity>,
        applicableUpdates: List<CollectedClaimUpdate>
    ): List<CollectedClaimEntity> {
        val entitiesToUpdate = applicableUpdates
            .filter { it.value != null }
            .mapNotNull { update ->
                val entity = existingEntityByClaimMap[update.claim.id]
                entity?.let { update to entity }
            }
            .mapNotNull { (update, entity) ->
                val newValue = collectedClaimUpdateMapper.toValue(update.value)
                if (newValue != entity.value) {
                    collectedClaimUpdateMapper.updateEntity(entity, update)
                } else null
            }
        return collectedClaimRepository.updateAll(entitiesToUpdate).toList()
    }

    internal suspend fun createMissingClaims(
        user: User,
        existingEntityByClaimMap: Map<String, CollectedClaimEntity>,
        applicableUpdates: List<CollectedClaimUpdate>
    ): List<CollectedClaimEntity> {
        val entitiesToCreate = applicableUpdates
            .filter { it.value != null }
            .filter { existingEntityByClaimMap[it.claim.id] == null }
            .map {
                collectedClaimUpdateMapper.toEntity(user.id, user.sessionId, it)
            }
        return collectedClaimRepository.saveAll(entitiesToCreate).toList()
    }

    /**
     * Mark the [claims] collected from the user validated.
     */
    suspend fun validateClaims(
        userId: UUID,
        claims: List<Claim>
    ) {
        claims.map(Claim::id).forEach {
            collectedClaimRepository.updateClaimsToVerified(
                userId = userId,
                claim = it
            )
        }
    }
}
