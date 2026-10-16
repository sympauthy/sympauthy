package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminUserClaimResource
import com.sympauthy.business.manager.collection.UserClaimCollectionManager.CollectedUserClaim
import com.sympauthy.business.manager.collection.UserClaimCollectionManager.GeneratedUserClaim
import com.sympauthy.business.manager.collection.UserClaimCollectionManager.UserClaim
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminUserClaimResourceMapper {

    /**
     * Publish [userClaim], reading its value from where its shape holds it.
     */
    fun toResource(userClaim: UserClaim): AdminUserClaimResource = when (userClaim) {
        is CollectedUserClaim -> toResourceFromCollectedClaim(
            claim = userClaim.claim,
            collectedClaim = userClaim.collectedClaim,
            identifier = userClaim.identifier
        )

        is GeneratedUserClaim -> toResourceFromGeneratedClaim(
            claim = userClaim.claim,
            identifier = userClaim.identifier,
            generatedClaimValue = userClaim.value
        )
    }

    private fun toResourceFromCollectedClaim(
        claim: EnabledClaim,
        collectedClaim: CollectedClaim?,
        identifier: Boolean
    ): AdminUserClaimResource {
        return AdminUserClaimResource(
            claimId = claim.id,
            value = collectedClaim?.value,
            type = toTypeString(claim.dataType),
            origin = toOrigin(claim),
            kind = toKindString(claim.kind),
            required = claim.required,
            identifier = identifier,
            group = toGroupString(claim.group),
            collectedAt = collectedClaim?.collectionDate,
            verifiedAt = collectedClaim?.verificationDate,
        )
    }

    private fun toResourceFromGeneratedClaim(
        claim: EnabledClaim,
        identifier: Boolean,
        generatedClaimValue: Any?
    ): AdminUserClaimResource {
        return AdminUserClaimResource(
            claimId = claim.id,
            value = generatedClaimValue,
            type = toTypeString(claim.dataType),
            origin = toOrigin(claim),
            kind = toKindString(claim.kind),
            required = claim.required,
            identifier = identifier,
            group = toGroupString(claim.group),
            collectedAt = null,
            verifiedAt = null,
        )
    }

    fun toTypeString(dataType: ClaimDataType): String = dataType.wireName

    fun toOrigin(claim: EnabledClaim): String = claim.origin.wireName

    fun toKindString(kind: ClaimKind?): String? = kind?.wireName

    fun toGroupString(group: ClaimGroup?): String? = group?.wireName
}
