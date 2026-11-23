package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.securitycontext.UserSecurityContextManager
import com.sympauthy.business.manager.user.ProvisionalAccountManager
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PreparedTerminalEffect
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
import com.sympauthy.monitoring.telemetry.FlowMetrics
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Advances the purposes of an [InteractiveFlowSession] to the single abstract step the end-user must go
 * through next.
 *
 * It is the only thing that mutates the session — inserting, completing and failing purposes — while each
 * [InteractiveFlowPurposeHandler] only reads it and describes what its purpose needs. How it walks the
 * purposes, and why completing is one transaction while preparing the terminal effects is not, is
 * `docs/design/interactive-flow.md`.
 */
@Singleton
open class InteractiveFlowEngine(
    @Inject private val purposeRegistry: InteractiveFlowPurposeRegistry,
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val provisionalAccountManager: ProvisionalAccountManager,
    @Inject private val userSecurityContextManager: UserSecurityContextManager,
    @Inject private val customSpanTracer: CustomSpanTracer
) {

    /**
     * Advance [session] to the next [InteractiveFlowStep] the end-user must go through, running any insertion
     * or completion it entails, and return it together with the possibly-transitioned session.
     *
     * **[InteractiveFlowStep.Complete] is answered to the call that completes the session**, and that call is
     * the one handing the end-user back. A session that was already completed when passed here is a later
     * request on it, and its initiating purpose decides what it gets: handed back again, or
     * [InteractiveFlowStep.AlreadyCompleted] where that purpose
     * [hands off once][InteractiveFlowPurposeHandler.handsOffOnce]. So a caller asks here once per request,
     * after the work that may have moved the flow forward, and maps that answer.
     *
     * A failed session maps to [InteractiveFlowStep.Error] and a cancelled one to [InteractiveFlowStep.Cancel].
     * No terminal session re-runs any effect.
     */
    suspend fun advance(session: InteractiveFlowSession): InteractiveFlowStepResult = customSpanTracer.advanceFlow {
        when (session) {
            is FailedInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Error)
            is CompletedInteractiveFlowSession -> InteractiveFlowStepResult(session, stepAfterCompletion(session))
            is CancelledInteractiveFlowSession -> InteractiveFlowStepResult(session, InteractiveFlowStep.Cancel)
            is OnGoingInteractiveFlowSession -> advanceOnGoing(session)
        }
    }

    private fun stepAfterCompletion(session: CompletedInteractiveFlowSession): InteractiveFlowStep {
        return if (purposeRegistry.getForPurpose(session.initiatingPurpose).handsOffOnce) {
            InteractiveFlowStep.AlreadyCompleted
        } else {
            InteractiveFlowStep.Complete
        }
    }

    /**
     * Return the purpose currently driving [session] — the first purpose (in order) whose handler still needs a
     * step — or null when every purpose has resolved.
     *
     * Read-only counterpart of the [advanceOnGoing] walk: it never mutates the session, so a caller can branch
     * on which purpose is active — between establishing identity
     * (e.g. [InteractiveFlowPurpose.OAUTH2_AUTHORIZE]) and confirming the already-fixed user
     * ([InteractiveFlowPurpose.REAUTHENTICATION]) — without advancing the flow.
     */
    suspend fun currentPurposeOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowPurpose? {
        return session.purposes.firstOrNull { purpose ->
            purposeRegistry.getForPurpose(purpose).nextStepOrNull(session) != null
        }
    }

    /**
     * Return the step [session] stands on — the one the first purpose still needing a step asks for — or null when
     * every purpose has resolved. Read-only, as [currentPurposeOrNull] is: it neither records a purpose complete nor
     * completes the session.
     */
    suspend fun currentStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        return session.purposes.firstNotNullOfOrNull { purpose ->
            purposeRegistry.getForPurpose(purpose).nextStepOrNull(session)
        }
    }

    private suspend fun advanceOnGoing(session: OnGoingInteractiveFlowSession): InteractiveFlowStepResult {
        // Walk the purposes in order; the first one that still needs a step yields it. `purposes` is re-read
        // every iteration so follow-up purposes inserted while resolving are visited too.
        var current = session
        var index = 0
        while (index < current.purposes.size) {
            when (val resolution = resolve(current, current.purposes[index])) {
                is PurposeResolution.Step -> return InteractiveFlowStepResult(current, resolution.step)
                is PurposeResolution.Resolved -> current = resolution.session
            }
            index++
        }
        return customSpanTracer.completeFlow { complete(current) }
    }

    /**
     * Ask the handler of [purpose] for the step it still needs in [session], or, once it needs none, mark it
     * complete and insert the follow-up purposes it declares right after it.
     *
     * Completion is recorded one purpose at a time as the engine hands off, even when a later purpose still yields
     * a step and the session as a whole is not yet complete.
     */
    private suspend fun resolve(
        session: OnGoingInteractiveFlowSession,
        purpose: InteractiveFlowPurpose
    ): PurposeResolution = customSpanTracer.resolvePurpose(purpose.metricName) {
        val handler = purposeRegistry.getForPurpose(purpose)
        val step = handler.nextStepOrNull(session)
        if (step != null) {
            PurposeResolution.Step(step)
        } else {
            val completed = sessionManager.markPurposeAsCompleted(session, purpose)
            PurposeResolution.Resolved(insertFollowUpsAfter(completed, purpose, handler))
        }
    }

    /**
     * What resolving a purpose came to: the [Step] it still needs, or the session it [Resolved] into.
     */
    private sealed interface PurposeResolution {
        class Step(val step: InteractiveFlowStep) : PurposeResolution
        class Resolved(val session: OnGoingInteractiveFlowSession) : PurposeResolution
    }

    /**
     * Insert the follow-up purposes [handler] declares for [session] — that are not already present —
     * immediately after the just-resolved [afterPurpose], in declared order, persisting each, and return the
     * possibly-grown session. Why right after it rather than at the end is `docs/design/interactive-flow.md`.
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
     * Every purpose has resolved and been marked complete: prepare each purpose's terminal effect, then commit
     * the completion, or fail the session on a terminal effect that refused it.
     *
     * The effects are prepared before the completion transaction opens, for the reason
     * `docs/design/interactive-flow.md` gives, so a refusal comes before anything is written and the session
     * is failed in a transaction of its own.
     */
    private suspend fun complete(session: OnGoingInteractiveFlowSession): InteractiveFlowStepResult {
        val effects = mutableListOf<PreparedTerminalEffect.Apply>()
        for (purpose in session.purposes) {
            when (val effect = purposeRegistry.getForPurpose(purpose).prepareTerminalEffect(session)) {
                is PreparedTerminalEffect.Apply -> effects.add(effect)
                PreparedTerminalEffect.None -> Unit
                is PreparedTerminalEffect.Fail -> return InteractiveFlowStepResult(
                    sessionManager.markAsFailedIfNotRecoverable(session, effect.error),
                    InteractiveFlowStep.Error
                )
            }
        }
        val completed = commitCompletion(session, effects)
        sessionManager.recordEnded(completed, completed.completeDate, FlowMetrics.SessionEnding.COMPLETED)
        // Once that transaction has committed, in one of its own. The account is real by then, so nothing
        // in user_security_contexts ever names one the abandoned-account sweep may delete — and a write
        // that can violate a unique index is kept out of a transaction whose rollback would answer a
        // person whose sign-in succeeded with a failure. It fails quietly for the same reason.
        userSecurityContextManager.fold(completed)
        return InteractiveFlowStepResult(completed, InteractiveFlowStep.Complete)
    }

    /**
     * Promote whatever the session signed up, transition it to completed, and apply each of the [effects]
     * prepared for its purposes in order — all in one transaction.
     *
     * The transaction is what makes "the account exists" and "the flow succeeded" the same fact. Without it,
     * a process dying between the two leaves rows owned by an account that will never be promoted — and the
     * cleaner then cannot delete that account, which is a foreign key it must not break rather than one row
     * left behind.
     *
     * Throws a non-recoverable [BusinessException] when the promotion finds an identifier taken, and the
     * concurrent-modification failure when the session moved or went since it was read; either rolls the whole
     * of it back.
     */
    @Transactional
    internal open suspend fun commitCompletion(
        session: OnGoingInteractiveFlowSession,
        effects: List<PreparedTerminalEffect.Apply>
    ): CompletedInteractiveFlowSession {
        // Promotion first, and the completion write before the effects: why, and what each guards against, is
        // `docs/design/provisional-user.md` and `docs/design/interactive-flow.md`.
        provisionalAccountManager.promote(session)
        val completed = sessionManager.markAsCompleted(session)
        effects.forEach { it.apply() }
        return completed
    }
}
