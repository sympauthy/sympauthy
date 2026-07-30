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
 * purpose has resolved, each purpose's terminal handoff runs, in order, marking its own purpose complete; the
 * session transitions to completed once the last outstanding purpose is completed (or to failed if any handoff
 * fails). The engine is the sole authority on cross-purpose sequencing; each handler only knows about its own
 * purpose.
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
    suspend fun getNextStep(session: InteractiveFlowSession): InteractiveFlowStepResult = when (session) {
        is FailedInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Error)
        is CompletedInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Complete)
        is OnGoingInteractiveFlowSession -> resolveOnGoing(session)
    }

    /**
     * Complete the [session] if the end-user has no remaining step to go through, and return the resulting
     * session — the completed session when it just finished, or [session] unchanged otherwise.
     *
     * Convenience over [getNextStep] for callers that only need to advance the flow and do not care about
     * the next step (e.g. after authenticating the user).
     */
    suspend fun completeIfNecessary(session: InteractiveFlowSession): InteractiveFlowSession {
        return getNextStep(session).session
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

        // Every purpose has resolved: hand each purpose to its handler, in order, to run its terminal effect
        // and mark its own purpose complete. The session becomes completed once the last outstanding purpose
        // is completed; a handoff may instead fail the session.
        return completePurposes(current)
    }

    private suspend fun completePurposes(session: OnGoingInteractiveFlowSession): InteractiveFlowStepResult {
        var current = session
        for (purpose in session.purposes) {
            when (val completed = purposeRegistry.getForPurpose(purpose).complete(current)) {
                is CompletedInteractiveFlowSession -> return InteractiveFlowStepResult(completed, InteractiveFlowStep.Complete)
                is FailedInteractiveFlowSession -> return InteractiveFlowStepResult(completed, InteractiveFlowStep.Error)
                is OnGoingInteractiveFlowSession -> current = completed
            }
        }
        // Completing the last purpose must have produced a completed (or failed) session.
        throw internalBusinessExceptionOf("flow.redirect.unhandled_status")
    }
}
