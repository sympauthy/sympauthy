package com.sympauthy.business.manager

import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.data.repository.CollectedClaimRepository
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Computes values for generated claims — claims whose values are produced by the authorization server
 * at runtime rather than collected from users (e.g. `sub`, `updated_at`).
 */
@Singleton
class GeneratedClaimsManager(
    private val claimManager: ClaimManager,
    private val collectedClaimRepository: CollectedClaimRepository
) {

    /**
     * Compute values for all enabled generated claims for the given user.
     */
    suspend fun computeValues(userId: UUID): Map<String, Any?> = computeValues(userId) {
        computeUpdatedAt(userId)
    }

    /**
     * Compute values for all enabled generated claims of the user [userId], reading `updated_at`
     * from the claims the caller already holds instead of querying for it.
     *
     * [collectedClaims] must be every claim collected from that user: `updated_at` is the date of
     * the most recent of them, and a subset of them answers with a date that is too old.
     */
    suspend fun computeValues(userId: UUID, collectedClaims: List<CollectedClaim>): Map<String, Any?> =
        computeValues(userId) { computeUpdatedAt(collectedClaims) }

    private suspend fun computeValues(userId: UUID, updatedAt: suspend () -> Long?): Map<String, Any?> {
        val generatedClaims = claimManager.listEnabledClaims().filter { it.generated }
        return generatedClaims.associate { claim ->
            claim.id to when (claim.id) {
                OpenIdConnectClaimId.SUB -> computeSubject(userId)
                OpenIdConnectClaimId.UPDATED_AT -> updatedAt()
                else -> null
            }
        }
    }

    /**
     * Compute the value of the `sub` claim for the given user.
     */
    fun computeSubject(userId: UUID): String = userId.toString()

    /**
     * Compute the value of the `updated_at` claim for the given user.
     * Returns the timestamp (as Unix epoch seconds) of the most recent claim collection, or null if no claims
     * have been collected.
     */
    suspend fun computeUpdatedAt(userId: UUID): Long? =
        collectedClaimRepository.findMaxCollectionDateByUserId(userId)?.epochSecond

    /**
     * Compute the value of the `updated_at` claim from every claim collected from a user.
     * Returns the timestamp (as Unix epoch seconds) of the most recent of them, or null if the
     * user has none.
     */
    fun computeUpdatedAt(collectedClaims: List<CollectedClaim>): Long? =
        collectedClaims.maxOfOrNull { it.collectionDate }?.epochSecond

    private val LocalDateTime.epochSecond: Long get() = toInstant(ZoneOffset.UTC).epochSecond
}
