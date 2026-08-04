package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.InteractiveFlowSessionLinkProvider
import com.sympauthy.data.model.InteractiveFlowSessionLinkProviderEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [InteractiveFlowSessionLinkProviderEntity] to the
 * [InteractiveFlowSessionLinkProvider] business model.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class InteractiveFlowSessionLinkProviderMapper {

    fun toInteractiveFlowSessionLinkProvider(
        entity: InteractiveFlowSessionLinkProviderEntity
    ): InteractiveFlowSessionLinkProvider {
        return InteractiveFlowSessionLinkProvider(
            sessionId = entity.sessionId,
            providerId = entity.providerId,
        )
    }
}
