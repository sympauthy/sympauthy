package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.CollectedClaimUpdateMapper
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
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
     * Return the list of [CollectedClaim] collected from the user associated to the [authorizeAttempt].
     *
     * Only the claims that are readable according to the client and the scopes
     * of the [authorizeAttempt] will be returned.
     */
    suspend fun findClaimsReadableByAttempt(
        authorizeAttempt: AuthorizeAttempt
    ): List<CollectedClaim> {
        return when (authorizeAttempt) {
            is FailedAuthorizeAttempt -> emptyList()
            is OnGoingAuthorizeAttempt -> {
                if (authorizeAttempt.userId == null) {
                    return emptyList()
                }
                findReadableUserInfoByUserId(
                    userId = authorizeAttempt.userId,
                    scopes = authorizeAttempt.grantedScopes ?: authorizeAttempt.requestedScopes
                )
            }

            is CompletedAuthorizeAttempt -> {
                findReadableUserInfoByUserId(
                    userId = authorizeAttempt.userId,
                    scopes = authorizeAttempt.grantedScopes
                )
            }
        }
    }

    /**
     * Return the user info we have collected for the user identified by [userId].
     * [scopes] can be provided to return only the claims accessible by the provided [scopes].
     */
    suspend fun findReadableUserInfoByUserId(
        userId: UUID,
        scopes: List<String>? = null
    ): List<CollectedClaim> {
        var claims = collectedClaimRepository.findByUserId(userId)
            .asSequence()
            .mapNotNull(collectedClaimMapper::toCollectedClaim)
        if (scopes != null) {
            claims = claims.filter { it.claim.canBeRead(scopes) }
        }
        return claims.toList()
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
     * Update the claims collected for the [user] and return all the claims readable according to the [scopes].
     * Only claims that are editable according to the [scopes] will be modified. Other update will be ignored.
     *
     * If [scopes] is ```null```, all [updates] will be applied instead and return all claims will be returned.
     */
    @Transactional
    open suspend fun update(
        user: User,
        updates: List<CollectedClaimUpdate>,
        scopes: List<String>? = null
    ): List<CollectedClaim>  {
        val applicableUpdates = getApplicableUpdates(updates, scopes)
        val collectedClaims = applyUpdates(user, applicableUpdates)
        return if (scopes != null) {
            collectedClaims.filter { it.claim.canBeRead(scopes) }
        } else {
            collectedClaims
        }
    }

    /**
     * Update the claims collected for the [user] and return all the claims collected for the user
     * (including one previously collected but not updated by the call to this method).
     */
    internal suspend fun applyUpdates(
        user: User,
        applicableUpdates: List<CollectedClaimUpdate>
    ) : List<CollectedClaim> = coroutineScope {
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

    internal fun getApplicableUpdates(
        updates: List<CollectedClaimUpdate>,
        scopes: List<String>? = null
    ): List<CollectedClaimUpdate> {
        return if (scopes != null) {
            updates.filter { it.claim.canBeWritten(scopes) }
        } else updates
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
