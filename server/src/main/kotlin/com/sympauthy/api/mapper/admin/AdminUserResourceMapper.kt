package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.business.manager.collection.UserCollectionManager.UserWithClaims
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminUserResourceMapper {

    /**
     * Publish [userWithClaims], carrying the values it holds for [selectedClaims].
     */
    fun toResource(
        userWithClaims: UserWithClaims,
        selectedClaims: List<EnabledClaim>?
    ): AdminUserResource {
        return AdminUserResource(
            userId = userWithClaims.user.id,
            status = userWithClaims.user.status.wireName,
            createdAt = userWithClaims.user.creationDate,
            lastAuthenticatedAt = userWithClaims.user.lastAuthenticationDate,
            lastRefreshedAt = userWithClaims.user.lastRefreshDate,
            claims = buildClaimsMap(userWithClaims, selectedClaims)
        )
    }

    /**
     * Publish [user], carrying the values it holds for [claims] and nothing else.
     *
     * The twin of the collection's [toResource] for a surface that has already read exactly the claims it
     * publishes — the identifier claims a session names its person by — rather than selecting them out of
     * everything the user holds. Nothing is computed here for that reason: a generated value is one this
     * server derives for a claim, and a caller wanting those is reading the user collection.
     *
     * It states neither of the person's last dates: a session is read under a scope that does not cover users,
     * so someone allowed to read flows would otherwise be reading a person's activity.
     */
    fun toResource(
        user: User,
        claims: List<CollectedClaim>
    ): AdminUserResource {
        return AdminUserResource(
            userId = user.id,
            status = user.status.wireName,
            createdAt = user.creationDate,
            lastAuthenticatedAt = null,
            lastRefreshedAt = null,
            claims = claims.associate { it.claim.id to it.value }
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
        selectedClaims: List<EnabledClaim>?
    ): Map<String, Any?>? {
        if (selectedClaims == null) return null
        val selectedIds = selectedClaims.map { it.id }.toSet()
        val collectedValues = userWithClaims.collectedClaims
            .filter { it.claim.id in selectedIds }
            .associate { it.claim.id to it.value }
        return selectedIds.associateWith { userWithClaims.generatedClaimValues[it] ?: collectedValues[it] }
    }
}
