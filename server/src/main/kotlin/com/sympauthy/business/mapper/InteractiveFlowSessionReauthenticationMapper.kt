package com.sympauthy.business.mapper

import com.sympauthy.business.model.flow.InteractiveFlowSessionReauthentication
import com.sympauthy.data.model.InteractiveFlowSessionReauthenticationEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [InteractiveFlowSessionReauthenticationEntity] to the
 * [InteractiveFlowSessionReauthentication] business model.
 */
@Singleton
class InteractiveFlowSessionReauthenticationMapper {

    fun toInteractiveFlowSessionReauthentication(
        entity: InteractiveFlowSessionReauthenticationEntity
    ): InteractiveFlowSessionReauthentication {
        return InteractiveFlowSessionReauthentication(
            sessionId = entity.sessionId,
            primaryCredentialProvenDate = entity.primaryCredentialProvenDate,
        )
    }
}
