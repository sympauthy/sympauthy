package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
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
     * Return the list of [CollectedClaim] for the identifier claims collected from the user identified by [userId].
     *
     * Note: This method is not restricted by consent or scopes.
     * It is intended for use by the authorization server internals and admin endpoints.
     */
    suspend fun findIdentifierByUserId(userId: UUID): List<CollectedClaim> {
        return findByUserIdAndClaims(userId, claimManager.listIdentifierClaims())
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
     */
    @Transactional
    open suspend fun applyUpdates(
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
            .map(CollectedClaimEntity::claim).toSet()
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
                collectedClaimUpdateMapper.toEntity(user.id, it)
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
