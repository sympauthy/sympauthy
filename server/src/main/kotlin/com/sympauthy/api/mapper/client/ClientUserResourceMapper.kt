package com.sympauthy.api.mapper.client

import com.sympauthy.api.resource.client.ClientProviderResource
import com.sympauthy.api.resource.client.ClientUserResource
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.ClientUser
import com.sympauthy.business.model.user.CollectedClaim
import jakarta.inject.Singleton

@Singleton
class ClientUserResourceMapper {

    fun toResource(clientUser: ClientUser): ClientUserResource {
        return ClientUserResource(
            userId = clientUser.user.id,
            identifierClaims = toIdentifierClaimsMap(clientUser.identifierClaims),
            providers = clientUser.providers.map(::toProviderResource),
            consentedScopes = clientUser.consent.scopes,
            consentedAt = clientUser.consent.consentedAt,
        )
    }

    fun toIdentifierClaimsMap(claims: List<CollectedClaim>): Map<String, Any?> {
        return claims.associate { it.claim.id to it.value }
    }

    fun toProviderResource(providerUserInfo: ProviderUserInfo): ClientProviderResource {
        return ClientProviderResource(
            providerId = providerUserInfo.providerId,
            subject = providerUserInfo.userInfo.subject,
            linkedAt = providerUserInfo.linkDate,
        )
    }
}
