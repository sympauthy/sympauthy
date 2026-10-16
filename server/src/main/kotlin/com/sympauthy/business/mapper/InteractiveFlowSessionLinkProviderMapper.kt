package com.sympauthy.business.mapper

import com.sympauthy.business.model.flow.InteractiveFlowSessionLinkProvider
import com.sympauthy.data.model.InteractiveFlowSessionLinkProviderEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [InteractiveFlowSessionLinkProviderEntity] to the
 * [InteractiveFlowSessionLinkProvider] business model.
 */
@Singleton
class InteractiveFlowSessionLinkProviderMapper {

    fun toInteractiveFlowSessionLinkProvider(
        entity: InteractiveFlowSessionLinkProviderEntity
    ): InteractiveFlowSessionLinkProvider {
        return InteractiveFlowSessionLinkProvider(
            sessionId = entity.sessionId,
            providerId = entity.providerId,
        )
    }
}
