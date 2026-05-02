package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Singleton
class AdminUserResourceMapper(
    @Inject private val generatedClaimsManager: GeneratedClaimsManager
) {

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
     * Generated claim values are overlaid from [generatedClaimValues].
     */
    fun buildClaimsMap(
        collectedClaims: List<CollectedClaim>,
        selectedClaims: List<Claim>?,
        generatedClaimValues: Map<String, Any?>
    ): Map<String, Any?>? {
        if (selectedClaims == null) return null
        val selectedIds = selectedClaims.map { it.id }.toSet()
        val claimValueMap = collectedClaims
            .filter { it.claim.id in selectedIds }
            .associate { it.claim.id to it.value }
        // Include all selected claims, even if no value was collected
        return selectedIds.associateWith { generatedClaimValues[it] ?: claimValueMap[it] }
    }
}
