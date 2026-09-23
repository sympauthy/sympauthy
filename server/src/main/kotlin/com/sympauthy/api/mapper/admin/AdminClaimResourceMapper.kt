package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminClaimResource
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimPublication
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class AdminClaimResourceMapper(
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    fun toResource(claim: Claim): AdminClaimResource {
        val identifierClaimIds = uncheckedAuthConfig.orThrow()
            .identifierClaims
            .toSet()
        return AdminClaimResource(
            id = claim.id,
            type = claim.dataType.wireName,
            origin = claim.origin.wireName,
            enabled = claim.enabled,
            required = claim.required,
            identifier = claim.id in identifierClaimIds,
            allowedValues = claim.allowedValues,
            group = claim.group?.wireName,
            // Listed in the enum's own order rather than the file's, so the answer is the same however a
            // deployment happened to write the two channels.
            publishedIn = ClaimPublication.entries.filter(claim::isPublishedIn).map { it.wireName }
        )
    }
}
