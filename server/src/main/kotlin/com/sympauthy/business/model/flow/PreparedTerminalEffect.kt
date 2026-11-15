package com.sympauthy.business.model.flow

import com.sympauthy.business.exception.BusinessException

/**
 * A purpose's terminal effect as its handler prepared it, by
 * [com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler.prepareTerminalEffect] once every purpose of
 * a session has resolved.
 *
 * The effect is the purpose's own concern-specific work (e.g. the OAuth2 purpose granting scopes and recording
 * consent); marking the purpose complete and completing the session is the engine's job, not the handler's.
 */
sealed interface PreparedTerminalEffect {

    /**
     * The terminal effect is decided, [apply] writes it and [afterCommit] does what a rollback could not take back.
     *
     * The engine runs [apply] inside the completion transaction, after the promotion and the completion write,
     * so it does no I/O beyond the database: the promotion's locks are held until that transaction commits.
     */
    fun interface Apply : PreparedTerminalEffect {
        suspend fun apply()

        /**
         * Run once the completion transaction has committed, and never for a completion that rolled back: a mail
         * queued there would reach a person whose flow did not complete.
         *
         * The flow has completed by then, so a failure here is logged rather than answered to the end-user, and
         * nothing retries it. The default does nothing.
         */
        suspend fun afterCommit() {}
    }

    /**
     * The purpose has no terminal effect, and the flow completes without one.
     */
    data object None : PreparedTerminalEffect

    /**
     * The terminal effect cannot let the flow complete; the engine fails the session with [error] without
     * opening the completion transaction.
     */
    data class Fail(val error: BusinessException) : PreparedTerminalEffect
}
