package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.flow.CollectableClaimResource
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.server.DisplayMessages
import com.sympauthy.util.wireName
import io.micronaut.context.MessageSource
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale

@Singleton
class CollectableClaimResourceMapper(
    @Inject @param:DisplayMessages private val displayMessageSource: MessageSource
) {

    fun toResources(claims: List<EnabledClaim>, locale: Locale): List<CollectableClaimResource> =
        claims.map { toResource(it, locale) }

    fun toResource(claim: EnabledClaim, locale: Locale): CollectableClaimResource =
        CollectableClaimResource(
            id = claim.id,
            required = claim.required,
            name = displayMessageSource.getMessage("claims.${claim.id}.name", claim.id, locale),
            group = claim.group?.wireName,
            type = claim.dataType.wireName
        )
}
