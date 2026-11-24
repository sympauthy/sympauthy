package com.sympauthy.api.flow.mapper

import com.sympauthy.api.flow.resource.ClaimValueResource
import com.sympauthy.api.flow.resource.ClaimsFlowResource
import com.sympauthy.api.flow.resource.ClientReferenceResource
import com.sympauthy.business.claim.model.CollectedClaim
import com.sympauthy.business.claim.model.EnabledClaim
import com.sympauthy.business.provider.model.ProviderUserInfo
import com.sympauthy.platform.server.DisplayMessages
import com.sympauthy.platform.util.wireName
import io.micronaut.context.MessageSource
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

@Singleton
class ClaimsResourceMapper(
    @Inject @param:DisplayMessages private val displayMessageSource: MessageSource
) {

    fun toResource(
        collectableClaims: List<EnabledClaim>,
        collectedClaims: List<CollectedClaim>,
        providerUserInfoList: List<ProviderUserInfo>,
        client: ClientReferenceResource?,
        cancelUrl: String?,
        locale: Locale
    ): ClaimsFlowResource {
        val collectedByClaimId = collectedClaims.associateBy { it.claim.id }
        val sortedProviderUserInfoList = providerUserInfoList.sortedByDescending { it.changeDate }
        return ClaimsFlowResource(
            claims = collectableClaims.map { claim ->
                toResource(claim, collectedByClaimId[claim.id], sortedProviderUserInfoList, locale)
            },
            client = client,
            cancelUrl = cancelUrl
        )
    }

    fun toResource(redirectUri: URI): ClaimsFlowResource {
        return ClaimsFlowResource(
            redirectUrl = redirectUri.toString()
        )
    }

    private fun toResource(
        claim: EnabledClaim,
        collectedClaim: CollectedClaim?,
        sortedProviderUserInfoList: List<ProviderUserInfo>,
        locale: Locale
    ): ClaimValueResource {
        val suggestedValue = sortedProviderUserInfoList
            .firstNotNullOfOrNull { it.userInfo.getClaimValueOrNull(claim) }
        return ClaimValueResource(
            id = claim.id,
            required = claim.required,
            name = displayMessageSource.getMessage("claims.${claim.id}.name", claim.id, locale),
            type = claim.dataType.wireName,
            group = claim.group?.wireName,
            collected = collectedClaim != null,
            value = collectedClaim?.value,
            suggestedValue = suggestedValue
        )
    }
}
