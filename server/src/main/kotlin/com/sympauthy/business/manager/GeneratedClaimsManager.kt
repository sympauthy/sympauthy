package com.sympauthy.business.manager

import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.data.repository.CollectedClaimRepository
import jakarta.inject.Singleton
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
    suspend fun computeValues(userId: UUID): Map<String, Any?> {
        val generatedClaims = claimManager.listEnabledClaims().filter { it.generated }
        return generatedClaims.associate { claim ->
            claim.id to computeValue(claim.id, userId)
        }
    }

    private suspend fun computeValue(claimId: String, userId: UUID): Any? {
        return when (claimId) {
            OpenIdConnectClaimId.SUB -> computeSubject(userId)
            OpenIdConnectClaimId.UPDATED_AT -> computeUpdatedAt(userId)
            else -> null
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
        collectedClaimRepository.findMaxCollectionDateByUserId(userId)
            ?.toInstant(ZoneOffset.UTC)?.epochSecond
}
