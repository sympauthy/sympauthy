package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminUserClaimResource
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.StandardClaim
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class AdminUserClaimResourceMapper {

    @Mapping(source = "claim.id", target = "claimId")
    @Mapping(source = "claim.dataType", target = "type", qualifiedByName = ["toTypeString"])
    @Mapping(source = "claim", target = "standard", qualifiedByName = ["toStandard"])
    @Mapping(source = "claim.required", target = "required")
    @Mapping(source = "identifier", target = "identifier")
    @Mapping(source = "claim.group", target = "group", qualifiedByName = ["toGroupString"])
    @Mapping(source = "collectedClaim.value", target = "value")
    @Mapping(source = "collectedClaim.collectionDate", target = "collectedAt")
    @Mapping(source = "collectedClaim.verificationDate", target = "verifiedAt")
    abstract fun toResource(claim: Claim, collectedClaim: CollectedClaim?, identifier: Boolean): AdminUserClaimResource

    @Named("toTypeString")
    fun toTypeString(dataType: ClaimDataType): String = dataType.name.lowercase()

    @Named("toStandard")
    fun toStandard(claim: Claim): Boolean = claim is StandardClaim

    @Named("toGroupString")
    fun toGroupString(group: ClaimGroup?): String? = group?.name?.lowercase()
}
