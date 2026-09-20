package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
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

@Singleton
open class CollectedClaimManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val userManager: UserManager,
    @Inject private val lockManager: LockManager,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val collectedClaimMapper: CollectedClaimMapper,
    @Inject private val collectedClaimUpdateMapper: CollectedClaimUpdateMapper
) {

    /**
     * Return the list of [CollectedClaim] collected from the user identified by [userId].
     *
     * Note: This method is not restricted by consent or scopes and returns all claims for the user.
     * It is intended for use by the authorization server internals and admin endpoints.
     * For consent-restricted access, use [ConsentedClaimManager] instead.
     */
    suspend fun findByUserId(userId: UUID): List<CollectedClaim> {
        return collectedClaimRepository.findByUserId(userId)
            .asSequence()
            .mapNotNull(collectedClaimMapper::toCollectedClaim)
            .toList()
    }

    /**
     * Return the list of [CollectedClaim] for the given [claims] collected from the user identified by [userId].
     *
     * Note: This method is not restricted by consent or scopes.
     * It is intended for use by the authorization server internals and admin endpoints.
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
     * Note: This method is not restricted by consent or scopes, only by audience. It is what a caller acting
     * for one audience reads where consent does not apply to the question it is asking, a rule deciding a
     * grant being the case; [ConsentAwareCollectedClaimManager] is what applies both.
     */
    suspend fun findByUserIdAndAudience(userId: UUID, audienceId: String): List<CollectedClaim> {
        return findByUserIdAndClaims(
            userId = userId,
            claims = claimManager.listAllClaims().filter { it.belongsToAudience(audienceId) }
        )
    }

    /**
     * Return the list of [CollectedClaim] for the identifier claims collected from the user identified by [userId].
     *
     * Note: This method is not restricted by consent or scopes.
     * It is intended for use by the authorization server internals and admin endpoints.
     */
    suspend fun findIdentifierByUserId(userId: UUID): List<CollectedClaim> {
        return findByUserIdAndClaims(userId, claimManager.listIdentifierClaims())
    }

    /**
     * Return the identifier claims collected from the users identified by [userIds], for all of them at once.
     *
     * Note: This method is not restricted by consent or scopes.
     * It is intended for use by the authorization server internals and admin endpoints.
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
     * For consent-restricted updates, use [ConsentedClaimManager.update] instead.
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
     * already holds. This is the manager every writer of a collected claim goes through, and it is the only
     * place that uniqueness is enforced on a write: an account signing in with any of the configured
     * identifier claims means a value has to be unique across all of them rather than within one column, so
     * no constraint expresses it and nothing below this refuses it. Two accounts left holding one value make
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
        val identifierValues = getIdentifierValuesIn(applicableUpdates)
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
     * The identifier claim values [updates] would write, by the claim writing them, as `collected_claims`
     * spells them. An update clearing a claim is not one: it takes no value from anybody.
     *
     * Public because it is the one spelling of that map, and a caller about to ask
     * [UserManager.findTakenIdentifierOrNull] over a set of updates needs it in the spelling the
     * rows compare on. Writing it out again at the call site is a second definition of which updates count
     * and how their values are stored, and the two would have to be changed together.
     */
    fun getIdentifierValuesIn(updates: List<CollectedClaimUpdate>): Map<String, String> {
        val identifierClaims = claimManager.listIdentifierClaims().toSet()
        if (identifierClaims.isEmpty()) {
            return emptyMap()
        }
        return updates
            .filter { it.claim in identifierClaims }
            .mapNotNull { update ->
                collectedClaimUpdateMapper.toValue(update.value)?.let { update.claim.id to it }
            }
            .toMap()
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
