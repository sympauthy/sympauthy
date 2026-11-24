package com.sympauthy.api.application.client.mapper

import com.sympauthy.api.application.client.resource.ClientProviderResource
import com.sympauthy.api.application.client.resource.ClientUserResource
import com.sympauthy.business.claim.model.CollectedClaim
import com.sympauthy.business.provider.model.ProviderUserInfo
import com.sympauthy.business.user.model.ClientUser
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
