package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.InteractiveFlowSessionReauthentication
import com.sympauthy.data.model.InteractiveFlowSessionReauthenticationEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [InteractiveFlowSessionReauthenticationEntity] to the
 * [InteractiveFlowSessionReauthentication] business model.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class InteractiveFlowSessionReauthenticationMapper {

    fun toInteractiveFlowSessionReauthentication(
        entity: InteractiveFlowSessionReauthenticationEntity
    ): InteractiveFlowSessionReauthentication {
        return InteractiveFlowSessionReauthentication(
            sessionId = entity.sessionId,
            primaryCredentialProvenDate = entity.primaryCredentialProvenDate,
        )
    }
}
