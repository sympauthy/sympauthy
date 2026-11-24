package com.sympauthy.api.application.user.mapper

import com.sympauthy.api.application.user.resource.UserClaimResource
import com.sympauthy.business.claim.model.CollectedClaim
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
