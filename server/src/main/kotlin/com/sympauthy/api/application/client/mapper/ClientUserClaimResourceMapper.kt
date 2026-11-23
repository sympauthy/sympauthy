package com.sympauthy.api.application.client.mapper

import com.sympauthy.api.application.client.resource.ClientUserClaimResource
import com.sympauthy.business.model.claim.CollectedClaim
import jakarta.inject.Singleton
import java.util.*

/**
 * Maps collected claims to [ClientUserClaimResource].
 */
@Singleton
class ClientUserClaimResourceMapper {

    fun toResource(
        userId: UUID,
        collectedClaims: List<CollectedClaim>,
        generatedClaimValues: Map<String, Any?> = emptyMap()
    ): ClientUserClaimResource {
        val claimsMap = collectedClaims.associate { it.claim.id to it.value } + generatedClaimValues
        return ClientUserClaimResource(
            userId = userId,
            claims = claimsMap
        )
    }
}
