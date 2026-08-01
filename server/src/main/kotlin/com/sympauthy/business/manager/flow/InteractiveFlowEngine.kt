package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.TerminalEffectResult
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Advances the purposes of an [InteractiveFlowSession] to the single abstract step the end-user must go
 * through next.
 *
 * The engine drives the purposes in order: it asks each [InteractiveFlowPurposeHandler], in turn, for the next
 * step of its purpose; the first purpose that still needs a step yields the step to present. As a purpose
 * resolves, the engine marks it complete and appends the follow-up purposes it declares — so completion is
 * recorded one purpose at a time as the engine hands off, even while later purposes still need steps. Once
 * every purpose has resolved, the engine runs each purpose's terminal effect and transitions the session to
 * completed (or failed). The engine is the sole owner of every session mutation — appending, completing,
 * failing — while the handlers only read the session and describe what their purpose needs.
 */
@Singleton
class InteractiveFlowEngine(
    @Inject private val purposeRegistry: InteractiveFlowPurposeRegistry,
    @Inject private val sessionManager: InteractiveFlowSessionManager
) {

    /**
     * Advance [session] to the next [InteractiveFlowStep] the end-user must go through, running any append /
     * completion mutation it entails, and return it together with the possibly-transitioned session.
     *
     * The call is idempotent for a session that is already terminal: a failed session maps to
     * [InteractiveFlowStep.Error], a completed one to [InteractiveFlowStep.Complete] and a cancelled one to
     * [InteractiveFlowStep.Cancel], without re-running any effect.
     */
    suspend fun advance(session: InteractiveFlowSession): InteractiveFlowStepResult = when (session) {
        is FailedInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Error)
        is CompletedInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Complete)
        is CancelledInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Cancel)
        is OnGoingInteractiveFlowSession -> advanceOnGoing(session)
    }

    /**
     * Advance [session] as far as it can go and return the resulting session — completed when it just
     * finished, ongoing when a step still remains, failed on a failed terminal effect.
     *
     * Convenience over [advance] for callers that only need to move the flow forward and do not care about the
     * next step (e.g. after authenticating the user).
     */
    suspend fun completeIfNecessary(session: InteractiveFlowSession): InteractiveFlowSession {
        return advance(session).session
    }

    private suspend fun advanceOnGoing(session: OnGoingInteractiveFlowSession): InteractiveFlowStepResult {
        // Walk the purposes in order; the first one that still needs a step yields it. `purposes` is re-read
        // every iteration so follow-up purposes appended while resolving are visited too.
        var current = session
        var index = 0
        while (index < current.purposes.size) {
            val purpose = current.purposes[index]
            val handler = purposeRegistry.getForPurpose(purpose)
            handler.nextStepOrNull(current)?.let { step ->
                return InteractiveFlowStepResult(current, step)
            }
            // The purpose has resolved: mark it complete as the engine hands off, then append the follow-up
            // purposes it declares. Completion is recorded one purpose at a time, even when a later purpose
            // still yields a step and the session as a whole is not yet complete.
            current = sessionManager.markPurposeAsCompleted(current, purpose)
            current = appendFollowUps(current, handler)
            index++
        }
        return complete(current)
    }

    /**
     * Append the follow-up purposes [handler] declares for [session] that are not already present, persisting
     * each, and return the possibly-grown session.
     */
    private suspend fun appendFollowUps(
        session: OnGoingInteractiveFlowSession,
        handler: InteractiveFlowPurposeHandler
    ): OnGoingInteractiveFlowSession {
        var current = session
        for (purpose in handler.followUpPurposes(current)) {
            if (purpose !in current.purposes) {
                current = sessionManager.appendPurpose(current, purpose)
            }
        }
        return current
    }

    /**
     * Every purpose has resolved and been marked complete: run each purpose's terminal effect in order, then
     * transition the session to completed. A failing terminal effect fails the session instead.
     *
     * Terminal effects run only here — once every purpose (e.g. the final MFA gate) has resolved — so a
     * purpose's concern-specific completion work (e.g. the OAuth2 purpose granting scopes and recording
     * consent) is committed only when the whole flow is about to succeed.
     */
    private suspend fun complete(session: OnGoingInteractiveFlowSession): InteractiveFlowStepResult {
        for (purpose in session.purposes) {
            val effect = purposeRegistry.getForPurpose(purpose).applyTerminalEffect(session)
            if (effect is TerminalEffectResult.Fail) {
                return InteractiveFlowStepResult(
                    sessionManager.markAsFailedIfNotRecoverable(session, effect.error),
                    InteractiveFlowStep.Error
                )
            }
        }
        return InteractiveFlowStepResult(
            sessionManager.markAsCompleted(session),
            InteractiveFlowStep.Complete
        )
    }
}
