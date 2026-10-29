package com.sympauthy.api.mapper.user

import com.sympauthy.api.resource.user.UserClaimResource
import com.sympauthy.business.model.user.CollectedClaim
import jakarta.inject.Singleton

@Singleton
class UserClaimResourceMapper {

    fun toResource(
        collectedClaims: List<CollectedClaim>,
        generatedClaimValues: Map<String, Any?> = emptyMap()
    ): UserClaimResource {
        val claimsMap = collectedClaims.associate { it.claim.id to it.value } + generatedClaimValues
        return UserClaimResource(claims = claimsMap)
    }
}
