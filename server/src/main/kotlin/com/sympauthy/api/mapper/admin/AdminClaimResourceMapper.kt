package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminClaimResource
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.StandardClaim
import jakarta.inject.Singleton

@Singleton
class AdminClaimResourceMapper {

    fun toResource(claim: Claim): AdminClaimResource {
        return AdminClaimResource(
            id = claim.id,
            type = claim.dataType.name.lowercase(),
            standard = claim is StandardClaim,
            enabled = claim.enabled,
            required = claim.required,
            allowedValues = claim.allowedValues,
            group = claim.group?.toGroupString()
        )
    }

    private fun ClaimGroup.toGroupString(): String = name.lowercase()
}
