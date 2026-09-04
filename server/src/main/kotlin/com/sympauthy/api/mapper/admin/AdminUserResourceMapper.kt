package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.business.manager.user.UserSearchManager.UserWithClaims
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminUserResourceMapper {

    /**
     * Publish [userWithClaims], carrying the values it holds for [selectedClaims].
     */
    fun toResource(
        userWithClaims: UserWithClaims,
        selectedClaims: List<Claim>?
    ): AdminUserResource {
        return AdminUserResource(
            userId = userWithClaims.user.id,
            status = userWithClaims.user.status.wireName,
            createdAt = userWithClaims.user.creationDate,
            claims = buildClaimsMap(userWithClaims, selectedClaims)
        )
    }

    /**
     * The value the user holds for each of [selectedClaims], or null where the caller selected none
     * and the resource publishes no claim at all.
     *
     * A selected claim is in the map whether or not the user has a value for it, and a generated
     * claim takes the value computed for the user over anything collected under the same identifier.
     */
    private fun buildClaimsMap(
        userWithClaims: UserWithClaims,
        selectedClaims: List<Claim>?
    ): Map<String, Any?>? {
        if (selectedClaims == null) return null
        val selectedIds = selectedClaims.map { it.id }.toSet()
        val collectedValues = userWithClaims.collectedClaims
            .filter { it.claim.id in selectedIds }
            .associate { it.claim.id to it.value }
        return selectedIds.associateWith { userWithClaims.generatedClaimValues[it] ?: collectedValues[it] }
    }
}
