package com.sympauthy.business.flow.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.flow.model.ConfirmActionType
import com.sympauthy.business.flow.model.InteractiveFlowSessionConfirm
import com.sympauthy.data.model.InteractiveFlowSessionConfirmEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [InteractiveFlowSessionConfirmEntity] to the
 * [InteractiveFlowSessionConfirm] business model.
 *
 * If the row holds an action [ConfirmActionType] does not name, an internal [BusinessException]
 * "mapper.interactive_flow_session_confirm.invalid_property" is thrown.
 */
@Singleton
class InteractiveFlowSessionConfirmMapper {

    fun toInteractiveFlowSessionConfirm(
        entity: InteractiveFlowSessionConfirmEntity
    ): InteractiveFlowSessionConfirm {
        return InteractiveFlowSessionConfirm(
            sessionId = entity.sessionId,
            action = action(entity.action),
            clientId = entity.clientId,
            confirmedDate = entity.confirmedDate,
        )
    }

    /**
     * The action [action] names, refusing the row where it names none.
     */
    private fun action(action: String): ConfirmActionType {
        return try {
            ConfirmActionType.valueOf(action)
        } catch (e: IllegalArgumentException) {
            throw internalBusinessExceptionOf(
                detailsId = "mapper.interactive_flow_session_confirm.invalid_property",
                throwable = e,
                values = arrayOf("property" to "action")
            )
        }
    }
}
