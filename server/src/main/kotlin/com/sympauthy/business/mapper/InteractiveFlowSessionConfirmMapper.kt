package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.InteractiveFlowSessionConfirm
import com.sympauthy.data.model.InteractiveFlowSessionConfirmEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [InteractiveFlowSessionConfirmEntity] to the
 * [InteractiveFlowSessionConfirm] business model.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class InteractiveFlowSessionConfirmMapper {

    fun toInteractiveFlowSessionConfirm(
        entity: InteractiveFlowSessionConfirmEntity
    ): InteractiveFlowSessionConfirm {
        return InteractiveFlowSessionConfirm(
            sessionId = entity.sessionId,
            action = ConfirmActionType.valueOf(entity.action),
            clientId = entity.clientId,
            confirmedDate = entity.confirmedDate,
        )
    }
}
