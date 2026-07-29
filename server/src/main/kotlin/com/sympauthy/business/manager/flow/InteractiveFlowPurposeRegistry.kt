package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.flow.InteractiveFlowSession
import jakarta.inject.Singleton

/**
 * Resolves the [InteractiveFlowPurposeHandler] responsible for an [InteractiveFlowSession], keyed by its purpose.
 */
@Singleton
class InteractiveFlowPurposeRegistry(
    handlers: List<InteractiveFlowPurposeHandler>
) {

    private val handlersByPurpose = handlers.associateBy(InteractiveFlowPurposeHandler::purpose)

    /**
     * Return the [InteractiveFlowPurposeHandler] that owns the [session]'s purpose.
     *
     * Throws an internal [com.sympauthy.business.exception.BusinessException] if no handler is registered
     * for the session's purpose — a purpose without a handler is a programming error.
     */
    fun getForSession(session: InteractiveFlowSession): InteractiveFlowPurposeHandler {
        return handlersByPurpose[session.purpose]
            ?: throw internalBusinessExceptionOf(
                "flow.purpose.unsupported",
                "purpose" to session.purpose.name
            )
    }
}
