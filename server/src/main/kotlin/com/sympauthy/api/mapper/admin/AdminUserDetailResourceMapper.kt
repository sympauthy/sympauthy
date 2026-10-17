package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminUserDetailResourceMapper {

    fun toResource(user: User, identifierClaims: List<CollectedClaim>): AdminUserDetailResource {
        return AdminUserDetailResource(
            userId = user.id,
            status = toStatusString(user.status),
            createdAt = user.creationDate,
            identifierClaims = toClaimsMap(identifierClaims),
        )
    }

    fun toStatusString(status: UserStatus): String = status.wireName

    fun toClaimsMap(claims: List<CollectedClaim>): Map<String, Any?> =
        claims.associate { it.claim.id to it.value }
}
