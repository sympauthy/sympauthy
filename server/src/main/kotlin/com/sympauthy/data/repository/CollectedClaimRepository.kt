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
 * Find any committed claim holding the value [claimValues] gives for the claim it is named under, or null
 * where none does and where [claimValues] is empty.
 *
 * Each claim carries its own value because a value is spelled the way its claim's data type spells it: one
 * address reaches an `email` claim lowercased and a `string` claim as it was typed, so a single string
 * offered to the whole set would be looked up under claims that store it differently. A caller offers each
 * claim the value that claim would have stored, never the one as it was typed.
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
                claimValues.forEach { (claimId, value) ->
                    and {
                        root[CollectedClaimEntity::claim] eq claimId
                        root[CollectedClaimEntity::value] eq value
                    }
                }
            }
        }
    })
}

/**
 * Find any committed claim (whose id is included in the [claimIds]) that matches one of the value in
 * [claimValues].
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
                    root[CollectedClaimEntity::value] eq it
                }
            }
        }
    }
    return findAll(PredicateSpecification.where(criteria)).toList()
}

/**
 * Find all distinct user IDs that have committed collected claims matching ALL entries in [claimValues].
 * Each entry maps a claim ID to its expected value.
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
                claimValues.forEach { (claimId, value) ->
                    and {
                        root[CollectedClaimEntity::claim] eq claimId
                        root[CollectedClaimEntity::value] eq value
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
