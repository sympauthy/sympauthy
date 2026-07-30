package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import jakarta.inject.Singleton

/**
 * Resolves the [InteractiveFlowPurposeHandler] responsible for a given [InteractiveFlowPurpose].
 */
@Singleton
class InteractiveFlowPurposeRegistry(
    handlers: List<InteractiveFlowPurposeHandler>
) {

    private val handlersByPurpose = handlers.associateBy(InteractiveFlowPurposeHandler::purpose)

    /**
     * Return the [InteractiveFlowPurposeHandler] that owns the [purpose].
     *
     * Throws an internal [com.sympauthy.business.exception.BusinessException] if no handler is registered for
     * the purpose — a purpose without a handler is a programming error.
     */
    fun getForPurpose(purpose: InteractiveFlowPurpose): InteractiveFlowPurposeHandler {
        return handlersByPurpose[purpose]
            ?: throw internalBusinessExceptionOf(
                "flow.purpose.unsupported",
                "purpose" to purpose.name
            )
    }
}
