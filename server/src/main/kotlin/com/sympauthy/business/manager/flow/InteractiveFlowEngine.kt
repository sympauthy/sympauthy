package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.user.ProvisionalAccountManager
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.TerminalEffectResult
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Advances the purposes of an [InteractiveFlowSession] to the single abstract step the end-user must go
 * through next.
 *
 * The engine drives the purposes in order: it asks each [InteractiveFlowPurposeHandler], in turn, for the next
 * step of its purpose; the first purpose that still needs a step yields the step to present. As a purpose
 * resolves, the engine marks it complete and inserts the follow-up purposes it declares right after it — so
 * completion is recorded one purpose at a time as the engine hands off, even while later purposes still need
 * steps. Once
 * every purpose has resolved, the engine runs each purpose's terminal effect and transitions the session to
 * completed (or failed). The engine is the sole owner of every session mutation — appending, completing,
 * failing — while the handlers only read the session and describe what their purpose needs.
 *
 * **Completing is one transaction.** The terminal effects, the promotion of whatever the session signed up
 * and the completion write commit together or not at all — see [commitCompletion]. Everything before that is
 * deliberately not: a flow the end-user is still walking through is many requests, and the point of
 * [com.sympauthy.data.model.SessionScoped] is that the rows those requests write need no transaction to span
 * them.
 */
@Singleton
open class InteractiveFlowEngine(
    @Inject private val purposeRegistry: InteractiveFlowPurposeRegistry,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val provisionalAccountManager: ProvisionalAccountManager
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

    /**
     * Return the purpose currently driving [session] — the first purpose (in order) whose handler still needs a
     * step — or null when every purpose has resolved.
     *
     * Read-only counterpart of the [advanceOnGoing] walk: it never mutates the session, so a caller can branch
     * on which purpose is active without advancing the flow. Used by the sign-in paths to switch between
     * establishing identity (e.g. [InteractiveFlowPurpose.OAUTH2_AUTHORIZE]) and confirming the already-fixed
     * user ([InteractiveFlowPurpose.REAUTHENTICATION]).
     */
    suspend fun currentPurposeOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowPurpose? {
        return session.purposes.firstOrNull { purpose ->
            purposeRegistry.getForPurpose(purpose).nextStepOrNull(session) != null
        }
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
            // The purpose has resolved: mark it complete as the engine hands off, then insert the follow-up
            // purposes it declares right after it. Completion is recorded one purpose at a time, even when a
            // later purpose still yields a step and the session as a whole is not yet complete.
            current = sessionManager.markPurposeAsCompleted(current, purpose)
            current = insertFollowUpsAfter(current, purpose, handler)
            index++
        }
        return complete(current)
    }

    /**
     * Insert the follow-up purposes [handler] declares for [session] — that are not already present —
     * immediately after the just-resolved [afterPurpose], in declared order, persisting each, and return the
     * possibly-grown session.
     *
     * Inserting right after the resolving purpose (rather than appending at the end) keeps a follow-up ahead
     * of any later purpose the session already carries: a re-authentication gate's MFA challenge lands before
     * the sensitive purpose it guards (e.g. a provider link), never after it.
     */
    private suspend fun insertFollowUpsAfter(
        session: OnGoingInteractiveFlowSession,
        afterPurpose: InteractiveFlowPurpose,
        handler: InteractiveFlowPurposeHandler
    ): OnGoingInteractiveFlowSession {
        var current = session
        var previous = afterPurpose
        for (purpose in handler.followUpPurposes(current)) {
            if (purpose !in current.purposes) {
                current = sessionManager.insertPurposeAfter(current, purpose, previous)
            }
            // Advance the anchor to `purpose` whether or not it was inserted, so several follow-ups keep their
            // declared order after the resolving purpose — a later new follow-up lands after an already-present
            // earlier sibling, not back at the resolving purpose.
            previous = purpose
        }
        return current
    }

    /**
     * Every purpose has resolved and been marked complete: commit the completion, or fail the session on a
     * terminal effect that refused it.
     *
     * The refusal is carried out of the transaction as an exception rather than returned, so a terminal
     * effect that failed after an earlier one already wrote takes that write down with it. The session is
     * then failed outside, in a transaction of its own — the one write that must survive.
     */
    private suspend fun complete(session: OnGoingInteractiveFlowSession): InteractiveFlowStepResult {
        return try {
            InteractiveFlowStepResult(commitCompletion(session), InteractiveFlowStep.Complete)
        } catch (refused: TerminalEffectRefusedException) {
            InteractiveFlowStepResult(
                sessionManager.markAsFailedIfNotRecoverable(session, refused.error),
                InteractiveFlowStep.Error
            )
        }
    }

    /**
     * Run each purpose's terminal effect in order, promote whatever the session signed up, and transition it
     * to completed — all in one transaction.
     *
     * Terminal effects run only here — once every purpose (e.g. the final MFA gate) has resolved — so a
     * purpose's concern-specific completion work (e.g. the OAuth2 purpose granting scopes, recording consent
     * and consuming the invitation) is committed only when the whole flow is about to succeed.
     *
     * The transaction is what makes "the account exists" and "the flow succeeded" the same fact. Without it,
     * a process dying between the two leaves rows owned by an account that will never be promoted — and the
     * cleaner then cannot delete that account, which is a foreign key it must not break rather than one row
     * left behind.
     *
     * Throws [TerminalEffectRefusedException] when a purpose refuses to complete, and a non-recoverable
     * [BusinessException] when the promotion finds an identifier taken; either rolls the whole of it back.
     */
    @Transactional
    internal open suspend fun commitCompletion(
        session: OnGoingInteractiveFlowSession
    ): CompletedInteractiveFlowSession {
        for (purpose in session.purposes) {
            val effect = purposeRegistry.getForPurpose(purpose).applyTerminalEffect(session)
            if (effect is TerminalEffectResult.Fail) {
                throw TerminalEffectRefusedException(effect.error)
            }
        }
        session.userId?.let { provisionalAccountManager.promote(session.id, it) }
        return sessionManager.markAsCompleted(session)
    }
}

/**
 * Carries a [TerminalEffectResult.Fail] out of the completion transaction so it rolls back.
 *
 * Not a [BusinessException]: this never reaches the API boundary. The engine catches it and fails the
 * session with the [error] it holds, which is the exception the end-user is actually answered with.
 */
private class TerminalEffectRefusedException(val error: BusinessException) : RuntimeException(error)
