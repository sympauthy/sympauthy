package com.sympauthy.api.flow.mapper

import com.sympauthy.api.flow.resource.CollectableClaimResource
import com.sympauthy.business.claim.model.EnabledClaim
import com.sympauthy.platform.server.DisplayMessages
import com.sympauthy.platform.util.wireName
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
