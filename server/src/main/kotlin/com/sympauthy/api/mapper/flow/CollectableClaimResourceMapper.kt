package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.flow.CollectableClaimResource
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.wireName
import com.sympauthy.server.DisplayMessages
import io.micronaut.context.MessageSource
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale

@Singleton
class CollectableClaimResourceMapper(
    @Inject @param:DisplayMessages private val displayMessageSource: MessageSource
) {

    fun toResources(claims: List<Claim>, locale: Locale): List<CollectableClaimResource> =
        claims.map { toResource(it, locale) }

    fun toResource(claim: Claim, locale: Locale): CollectableClaimResource =
        CollectableClaimResource(
            id = claim.id,
            required = claim.required,
            name = displayMessageSource.getMessage("claims.${claim.id}.name", claim.id, locale),
            group = claim.group?.name?.lowercase(),
            type = claim.dataType.wireName
        )
}
