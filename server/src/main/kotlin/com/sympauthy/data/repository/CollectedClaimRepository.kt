package com.sympauthy.data.repository

import com.sympauthy.data.model.CollectedClaimEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.where
import kotlinx.coroutines.flow.toList
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*

interface CollectedClaimRepository : CoroutineCrudRepository<CollectedClaimEntity, UUID>,
    CoroutineJpaSpecificationExecutor<CollectedClaimEntity> {

    suspend fun findByUserId(userId: UUID): List<CollectedClaimEntity>

    suspend fun findByUserIdAndClaimInList(userId: UUID, claim: List<String>): List<CollectedClaimEntity>

    suspend fun findByUserIdInList(userId: List<UUID>): List<CollectedClaimEntity>

    suspend fun findByUserIdInListAndClaimInList(userId: List<UUID>, claim: List<String>): List<CollectedClaimEntity>

    @Query("SELECT MAX(c.collection_date) FROM collected_claims c WHERE c.user_id = :userId")
    suspend fun findMaxCollectionDateByUserId(userId: UUID): LocalDateTime?

    /**
     * Set verified on a collected [claim] for a given user (identified by [userId]) and update the verification date to
     * now. If the verified is already at true, the verification date will not be updated to keep the original date.
     */
    @Query(
        """
        UPDATE collected_claims SET
        verification_date = CASE WHEN verified IS TRUE THEN verification_date ELSE :verificationDate END,
        verified = TRUE
        WHERE user_id = :userId and claim = :claim
        """
    )
    suspend fun updateClaimsToVerified(
        userId: UUID,
        claim: String,
        verificationDate: LocalDateTime = now()
    )

    /**
     * Promote every claim the account [userId] owns and the interactive flow session
     * [sessionId] collected, making them permanent, and answer how many there were.
     */
    @Query("UPDATE collected_claims SET session_id = NULL WHERE user_id = :userId AND session_id = :sessionId")
    suspend fun clearSessionId(userId: UUID, sessionId: UUID): Int

    /**
     * Collect the claims the accounts [userId] still hold provisionally, and answer how many there
     * were. Provisionality is re-asserted here rather than trusted from the read that selected them.
     */
    suspend fun deleteByUserIdInAndSessionIdIsNotNull(userId: List<UUID>): Int
}

/**
 * Find any committed claim whose [CollectedClaimEntity.comparisonValue] is the one [claimValues] gives for
 * the claim it is named under, or null where none is and where [claimValues] is empty.
 *
 * The comparison value rather than the stored one, so that a value reaches its row however the person who
 * owns it capitalised it. Each claim carries its own because a claim cleans a value its own way before it
 * is folded — a number claim reads `0042` as `42` where a string one does not — and because a claim that
 * could hold no such value at all is left out rather than matched. A caller asks
 * [com.sympauthy.business.manager.user.CollectedClaimManager.getIdentifierComparisonValuesOf] for that map.
 *
 * A claim a session is still signing up is excluded: this is how an identifier is resolved to an account,
 * so answering with a provisional row would hand a caller an account that does not exist yet. See
 * [com.sympauthy.data.model.SessionScoped].
 */
suspend fun CollectedClaimRepository.findAnyClaimMatching(
    claimValues: Map<String, String>
): CollectedClaimEntity? {
    if (claimValues.isEmpty()) {
        return null
    }
    return findOne(where {
        and {
            root[CollectedClaimEntity::sessionId].equalsNull()
            or {
                claimValues.forEach { (claimId, comparisonValue) ->
                    and {
                        root[CollectedClaimEntity::claim] eq claimId
                        root[CollectedClaimEntity::comparisonValue] eq comparisonValue
                    }
                }
            }
        }
    })
}

/**
 * Find any committed claim (whose id is included in the [claimIds]) whose
 * [CollectedClaimEntity.comparisonValue] is one of [claimValues].
 *
 * The comparison value rather than the stored one: a value belongs to one account however it was
 * capitalised, and this is the read that says whether it is free.
 *
 * A claim a session is still signing up is excluded, which is what lets two sign-ups hold the same
 * identifier at once: neither blocks the other, and the collision is settled when the first of them
 * promotes. See [com.sympauthy.data.model.SessionScoped].
 */
suspend fun CollectedClaimRepository.findAnyClaimMatching(
    claimIds: List<String>,
    claimValues: List<String>,
): List<CollectedClaimEntity> {
    if (claimIds.isEmpty() || claimValues.isEmpty()) {
        return emptyList()
    }
    // Do not understand but the 'in' does not seem to work properly.
    val criteria = where {
        and {
            root[CollectedClaimEntity::sessionId].equalsNull()
            or {
                claimIds.forEach {
                    root[CollectedClaimEntity::claim] eq it
                }
            }
            or {
                claimValues.forEach {
                    root[CollectedClaimEntity::comparisonValue] eq it
                }
            }
        }
    }
    return findAll(PredicateSpecification.where(criteria)).toList()
}

/**
 * Find all distinct user IDs that have committed collected claims matching ALL entries in [claimValues].
 * Each entry maps a claim ID to the [CollectedClaimEntity.comparisonValue] expected under it.
 *
 * A user matches only if they have a matching value for every claim in the map. A claim a session is still
 * signing up is excluded, so an account this server has not finished creating never matches an identifier.
 * See [com.sympauthy.data.model.SessionScoped].
 */
suspend fun CollectedClaimRepository.findUserIdsMatchingAllClaims(
    claimValues: Map<String, String?>,
): List<UUID> {
    if (claimValues.isEmpty()) {
        return emptyList()
    }
    val criteria = where<CollectedClaimEntity> {
        and {
            root[CollectedClaimEntity::sessionId].equalsNull()
            or {
                claimValues.forEach { (claimId, comparisonValue) ->
                    and {
                        root[CollectedClaimEntity::claim] eq claimId
                        root[CollectedClaimEntity::comparisonValue] eq comparisonValue
                    }
                }
            }
        }
    }
    val entities = findAll(PredicateSpecification.where(criteria)).toList()
    val expectedClaimIds = claimValues.keys
    return entities
        .groupBy { it.userId }
        .filterValues { matched ->
            expectedClaimIds.all { claimId -> matched.any { it.claim == claimId } }
        }
        .keys
        .toList()
}
