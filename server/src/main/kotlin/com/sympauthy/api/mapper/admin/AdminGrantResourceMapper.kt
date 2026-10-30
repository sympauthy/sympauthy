package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminGrantDetailResource
import com.sympauthy.api.resource.admin.AdminGrantRequestedClaimsResource
import com.sympauthy.api.resource.admin.AdminGrantSummaryResource
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.grant.GrantWithStatus
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminGrantResourceMapper {

    fun toSummaryResource(grantWithStatus: GrantWithStatus): AdminGrantSummaryResource {
        val (grant, status) = grantWithStatus
        return AdminGrantSummaryResource(
            grantId = grant.id,
            userId = grant.userId,
            clientId = grant.clientId,
            audienceId = grant.audienceId,
            grantType = grant.grantType.wireName,
            status = status.wireName,
            createdAt = grant.creationDate,
            expiresAt = grant.expirationDate,
            revokedAt = grant.revokedAt,
            revokedBy = grant.revokedBy?.wireName,
            actorGrantId = grant.actorGrantId
        )
    }

    /**
     * The detail of [grantWithStatus], naming who revoked it only where an administrator did: the identifier a
     * revocation records beside a consent revoked is whoever revoked that consent, which is the consent's to say.
     */
    fun toDetailResource(grantWithStatus: GrantWithStatus): AdminGrantDetailResource {
        val (grant, status) = grantWithStatus
        return AdminGrantDetailResource(
            grantId = grant.id,
            userId = grant.userId,
            clientId = grant.clientId,
            audienceId = grant.audienceId,
            grantType = grant.grantType.wireName,
            status = status.wireName,
            createdAt = grant.creationDate,
            expiresAt = grant.expirationDate,
            revokedAt = grant.revokedAt,
            revokedBy = grant.revokedBy?.wireName,
            revokedByUserId = grant.revokedById.takeIf { grant.revokedBy == GrantRevokedBy.ADMIN },
            actorGrantId = grant.actorGrantId,
            grantedScopes = grant.grantedScopes,
            grantedAt = grant.grantedAt,
            grantedBy = grant.grantedBy?.wireName,
            consentedScopes = grant.consentedScopes,
            consentedAt = grant.consentedAt,
            consentedBy = grant.consentedBy?.wireName,
            clientScopes = grant.clientScopes,
            authenticatedAt = grant.authenticationDate,
            requestedClaims = toRequestedClaimsResource(grant.requestedClaims)
        )
    }

    private fun toRequestedClaimsResource(requestedClaims: RequestedClaims): AdminGrantRequestedClaimsResource? {
        if (requestedClaims == RequestedClaims.NONE) return null
        return AdminGrantRequestedClaimsResource(
            idToken = requestedClaims.idTokenClaimIds.toList(),
            userinfo = requestedClaims.userInfoClaimIds.toList()
        )
    }
}
