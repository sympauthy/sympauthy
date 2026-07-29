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

    /**
     * Complete the [session] if the end-user has no remaining step to go through, and return the resulting
     * session — the completed session when it just finished, or [session] unchanged otherwise.
     *
     * Convenience over [getForSession] + [InteractiveFlowPurposeHandler.getCurrentStep] for callers that
     * only need to advance the flow and do not care about the next step (e.g. after authenticating the
     * user). Use `getForSession(session).getCurrentStep(session)` directly when the step itself is needed.
     */
    suspend fun completeIfNecessary(session: InteractiveFlowSession): InteractiveFlowSession {
        return getForSession(session).getCurrentStep(session).session
    }
}
