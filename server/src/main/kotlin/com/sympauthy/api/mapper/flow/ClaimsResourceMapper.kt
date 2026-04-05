package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.flow.ClaimValueResource
import com.sympauthy.api.resource.flow.ClaimsFlowResource
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.server.DisplayMessages
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
        collectableClaims: List<Claim>,
        collectedClaims: List<CollectedClaim>,
        providerUserInfoList: List<ProviderUserInfo>,
        locale: Locale
    ): ClaimsFlowResource {
        val collectedByClaimId = collectedClaims.associateBy { it.claim.id }
        val sortedProviderUserInfoList = providerUserInfoList.sortedByDescending { it.changeDate }
        return ClaimsFlowResource(
            claims = collectableClaims.map { claim ->
                toResource(claim, collectedByClaimId[claim.id], sortedProviderUserInfoList, locale)
            }
        )
    }

    fun toResource(redirectUri: URI): ClaimsFlowResource {
        return ClaimsFlowResource(
            redirectUrl = redirectUri.toString()
        )
    }

    private fun toResource(
        claim: Claim,
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
            type = claim.dataType.name.lowercase(),
            group = claim.group?.name?.lowercase(),
            collected = collectedClaim != null,
            value = collectedClaim?.value,
            suggestedValue = suggestedValue
        )
    }
}
