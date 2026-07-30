package com.sympauthy.business.model.flow

import com.sympauthy.business.exception.BusinessException

/**
 * The outcome of a purpose's terminal effect, run by
 * [com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler.applyTerminalEffect] once every purpose of
 * a session has resolved.
 *
 * The effect is the purpose's own concern-specific work (e.g. the OAuth2 purpose granting scopes and recording
 * consent); marking the purpose complete and completing the session is the engine's job, not the handler's.
 */
sealed interface TerminalEffectResult {

    /**
     * The terminal effect ran; the engine marks the purpose complete and moves on to the next one.
     */
    data object Proceed : TerminalEffectResult

    /**
     * The terminal effect cannot let the flow complete; the engine fails the session with [error].
     */
    data class Fail(val error: BusinessException) : TerminalEffectResult
}
