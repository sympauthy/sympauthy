package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminClaimResource
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.origin
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class AdminClaimResourceMapper(
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    fun toResource(claim: Claim): AdminClaimResource {
        val identifierClaimIds = uncheckedAuthConfig.orThrow()
            .identifierClaims
            .map { it.id }
            .toSet()
        return AdminClaimResource(
            id = claim.id,
            type = claim.dataType.name.lowercase(),
            origin = claim.origin.value,
            enabled = claim.enabled,
            required = claim.required,
            identifier = claim.id in identifierClaimIds,
            allowedValues = claim.allowedValues,
            group = claim.group?.toGroupString()
        )
    }

    private fun ClaimGroup.toGroupString(): String = name.lowercase()
}