package com.sympauthy.business.flow.mapper

import com.sympauthy.business.flow.model.InteractiveFlowSessionProvider
import com.sympauthy.data.model.InteractiveFlowSessionProviderEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [InteractiveFlowSessionProviderEntity] to the
 * [InteractiveFlowSessionProvider] business model.
 */
@Singleton
class InteractiveFlowSessionProviderMapper {

    fun toInteractiveFlowSessionProvider(
        entity: InteractiveFlowSessionProviderEntity
    ): InteractiveFlowSessionProvider {
        return InteractiveFlowSessionProvider(
            sessionId = entity.sessionId,
            providerId = entity.providerId,
            providerNonceJsonWebTokenId = entity.providerNonceJsonWebTokenId,
        )
    }
}
