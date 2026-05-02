package com.sympauthy.api.mapper.client

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.client.ClientProviderResource
import com.sympauthy.api.resource.client.ClientUserResource
import com.sympauthy.business.model.user.ClientUser
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.data.model.ProviderUserInfoEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class ClientUserResourceMapper {

    fun toResource(clientUser: ClientUser, generatedClaimValues: Map<String, Any?>): ClientUserResource {
        val resource = toResourceFromCollectedClaims(clientUser)
        return resource.copy(
            identifierClaims = resource.identifierClaims + generatedClaimValues.filterKeys { it !in resource.identifierClaims }
        )
    }

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "identifierClaims", target = "identifierClaims")
    @Mapping(source = "providers", target = "providers")
    @Mapping(source = "consent.scopes", target = "consentedScopes")
    @Mapping(source = "consent.consentedAt", target = "consentedAt")
    internal abstract fun toResourceFromCollectedClaims(clientUser: ClientUser): ClientUserResource

    fun toIdentifierClaimsMap(claims: List<CollectedClaim>): Map<String, Any?> {
        return claims.associate { it.claim.id to it.value }
    }

    @Mapping(source = "id.providerId", target = "providerId")
    @Mapping(source = "subject", target = "subject")
    @Mapping(source = "fetchDate", target = "linkedAt")
    abstract fun toProviderResource(entity: ProviderUserInfoEntity): ClientProviderResource
}
