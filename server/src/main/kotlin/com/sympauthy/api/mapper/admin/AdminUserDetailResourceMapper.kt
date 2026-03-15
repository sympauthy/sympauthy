package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class AdminUserDetailResourceMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.status", target = "status", qualifiedByName = ["toStatusString"])
    @Mapping(source = "user.creationDate", target = "createdAt")
    @Mapping(source = "identifierClaims", target = "identifierClaims", qualifiedByName = ["toClaimsMap"])
    abstract fun toResource(user: User, identifierClaims: List<CollectedClaim>): AdminUserDetailResource

    @Named("toStatusString")
    fun toStatusString(status: UserStatus): String = status.name.lowercase()

    @Named("toClaimsMap")
    fun toClaimsMap(claims: List<CollectedClaim>): Map<String, Any?> =
        claims.associate { it.claim.id to it.value }
}
