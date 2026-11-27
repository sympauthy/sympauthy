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
     * Set verified on a collected [claim] for a given user (identified by [userId]) and date the verification
     * [verificationDate]. If the verified is already at true, the verification date will not be updated to keep
     * the original date.
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
        verificationDate: LocalDateTime
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
 * Find the committed claims whose claim and [CollectedClaimEntity.foldedEqualityHash] are one of
 * [claimHashes], or nothing where it is empty.
 *
 * **These are candidates, not matches**: a caller acts on a row only after re-checking the folded value
 * itself, as `docs/design/identifier-claims.md` says. A claim a session is still signing up is excluded,
 * as for every lookup by identifier — see `docs/design/provisional-user.md`.
 */
suspend fun CollectedClaimRepository.findCommittedClaimsMatching(
    claimHashes: Collection<Pair<String, Long>>
): List<CollectedClaimEntity> {
    if (claimHashes.isEmpty()) {
        return emptyList()
    }
    // Do not understand but the 'in' does not seem to work properly.
    val criteria = where<CollectedClaimEntity> {
        and {
            root[CollectedClaimEntity::sessionId].equalsNull()
            or {
                claimHashes.forEach { (claimId, hash) ->
                    and {
                        root[CollectedClaimEntity::claim] eq claimId
                        root[CollectedClaimEntity::foldedEqualityHash] eq hash
                    }
                }
            }
        }
    }
    return findAll(PredicateSpecification.where(criteria)).toList()
}
