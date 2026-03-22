package com.sympauthy.api.mapper.client

import com.sympauthy.api.resource.client.ClientUserClaimResource
import com.sympauthy.business.model.user.CollectedClaim
import jakarta.inject.Singleton
import java.util.*

/**
 * Maps collected claims to [ClientUserClaimResource].
 *
 * Not a MapStruct mapper because the mapping involves aggregating a list of claims into a flat map,
 * which is better expressed as plain Kotlin.
 */
@Singleton
class ClientUserClaimResourceMapper {

    fun toResource(userId: UUID, claims: List<CollectedClaim>): ClientUserClaimResource {
        val claimsMap = claims.associate { it.claim.id to it.value }
        return ClientUserClaimResource(
            userId = userId,
            claims = claimsMap
        )
    }
}
