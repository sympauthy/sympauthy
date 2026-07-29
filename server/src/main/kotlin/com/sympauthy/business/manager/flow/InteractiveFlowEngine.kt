package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStepResult
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Sequences the purposes of an [InteractiveFlowSession] into the single abstract step the end-user must go
 * through next.
 *
 * The engine drives the purposes in order: it asks each [InteractiveFlowPurposeHandler], in turn, for the
 * next step of its purpose; the first purpose that is still pending yields the step to present. Once every
 * purpose has resolved, the initiating purpose's terminal handoff runs, transitioning the session to
 * completed (or failed). The engine is the sole authority on cross-purpose sequencing; each handler only
 * knows about its own purpose.
 */
@Singleton
class InteractiveFlowEngine(
    @Inject private val purposeRegistry: InteractiveFlowPurposeRegistry
) {

    /**
     * Compute the next [InteractiveFlowStep] for [session], running any completion transition, and return it
     * together with the possibly-transitioned session.
     *
     * The call is idempotent for a session that is already terminal: a failed session maps to
     * [InteractiveFlowStep.Error] and a completed one to [InteractiveFlowStep.Complete], without re-running
     * any effect.
     */
    suspend fun getCurrentStep(session: InteractiveFlowSession): InteractiveFlowStepResult = when (session) {
        is FailedInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Error)
        is CompletedInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Complete)
        is OnGoingInteractiveFlowSession -> resolveOnGoing(session)
    }

    /**
     * Complete the [session] if the end-user has no remaining step to go through, and return the resulting
     * session — the completed session when it just finished, or [session] unchanged otherwise.
     *
     * Convenience over [getCurrentStep] for callers that only need to advance the flow and do not care about
     * the next step (e.g. after authenticating the user).
     */
    suspend fun completeIfNecessary(session: InteractiveFlowSession): InteractiveFlowSession {
        return getCurrentStep(session).session
    }

    private suspend fun resolveOnGoing(session: OnGoingInteractiveFlowSession): InteractiveFlowStepResult {
        // Walk the purposes in order; the first one still pending yields the step to present. `purposes` is
        // re-read every iteration so purposes appended while resolving are visited too.
        var current = session
        var index = 0
        while (index < current.purposes.size) {
            val handler = purposeRegistry.getForPurpose(current.purposes[index])
            when (val result = handler.getNextStep(current)) {
                is InteractiveFlowPurposeStepResult.Pending ->
                    return InteractiveFlowStepResult(result.session, result.step)

                is InteractiveFlowPurposeStepResult.Resolved -> {
                    current = result.session
                    index++
                }
            }
        }

        // Every purpose has resolved: run the initiating purpose's terminal handoff and route on its outcome.
        val initiatingHandler = purposeRegistry.getForPurpose(current.initiatingPurpose)
        return when (val completed = initiatingHandler.complete(current)) {
            is CompletedInteractiveFlowSession -> InteractiveFlowStepResult(completed, InteractiveFlowStep.Complete)
            is FailedInteractiveFlowSession -> InteractiveFlowStepResult(completed, InteractiveFlowStep.Error)
            is OnGoingInteractiveFlowSession -> throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
        }
    }
}
