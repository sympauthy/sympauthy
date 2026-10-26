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
     * The terminal effect is decided, and [apply] writes it.
     *
     * The engine runs [apply] inside the completion transaction, after the promotion, so it does no I/O beyond
     * the database: the promotion's locks are held until that transaction commits.
     */
    fun interface Apply : PreparedTerminalEffect {
        suspend fun apply()
    }

    /**
     * The terminal effect cannot let the flow complete; the engine fails the session with [error] without
     * opening the completion transaction.
     */
    data class Fail(val error: BusinessException) : PreparedTerminalEffect
}
