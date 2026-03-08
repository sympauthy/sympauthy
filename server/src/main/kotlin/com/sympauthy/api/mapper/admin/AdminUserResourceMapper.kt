package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import jakarta.inject.Singleton

@Singleton
class AdminUserResourceMapper {

    fun toResource(
        user: User,
        claims: Map<String, Any?>? = null
    ): AdminUserResource {
        return AdminUserResource(
            userId = user.id,
            status = user.status.name.lowercase(),
            createdAt = user.creationDate,
            claims = claims
        )
    }

    /**
     * Build the claims map from collected claims, filtered by the selected claim IDs.
     * Returns null if [selectedClaims] is null (meaning claims were explicitly omitted).
     */
    fun buildClaimsMap(
        collectedClaims: List<CollectedClaim>,
        selectedClaims: List<Claim>?
    ): Map<String, Any?>? {
        if (selectedClaims == null) return null
        val selectedIds = selectedClaims.map { it.id }.toSet()
        val claimValueMap = collectedClaims
            .filter { it.claim.id in selectedIds }
            .associate { it.claim.id to it.value }
        // Include all selected claims, even if no value was collected
        return selectedIds.associateWith { claimValueMap[it] }
    }
}
