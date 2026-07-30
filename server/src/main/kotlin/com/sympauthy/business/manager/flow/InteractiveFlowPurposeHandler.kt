package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.TerminalEffectResult

/**
 * Owns the step logic and completion behaviour of a single [purpose] of an interactive flow session.
 *
 * There is one implementation per [InteractiveFlowPurpose]; the one responsible for a purpose is resolved via
 * [InteractiveFlowPurposeRegistry]. A session may carry several purposes, sequenced by [InteractiveFlowEngine].
 *
 * A handler only ever reads the session and describes what its purpose needs — it never mutates or persists the
 * session. Appending purposes, marking a purpose complete, completing or failing the session is the engine's
 * sole responsibility, driven by the answers below.
 */
interface InteractiveFlowPurposeHandler {

    /**
     * The purpose this handler is responsible for.
     */
    val purpose: InteractiveFlowPurpose

    /**
     * The next step this purpose requires the end-user to go through on the ongoing [session], or null once
     * every step it requires is satisfied (the purpose is resolved).
     *
     * A pure query: the step is abstract, and mapping it to a concrete redirect (web) or another transport
     * happens at the API boundary.
     */
    suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep?

    /**
     * The follow-up purposes that must run once this purpose is resolved (e.g. the OAuth2 authorize purpose
     * requiring an MFA purpose before the flow can complete), evaluated against the ongoing [session].
     *
     * The engine appends any of these not already present. Returning purposes already on the session is
     * harmless; the default is none.
     */
    suspend fun followUpPurposes(session: OnGoingInteractiveFlowSession): List<InteractiveFlowPurpose> = emptyList()

    /**
     * Run this purpose's own terminal effect on the ongoing [session] — its concern-specific completion work
     * (e.g. the OAuth2 purpose granting scopes and recording consent).
     *
     * Called by [InteractiveFlowEngine] once every purpose has resolved, for each purpose in order, before the
     * engine marks the purpose complete. [TerminalEffectResult.Fail] tells the engine to fail the session
     * instead. The default is a no-op ([TerminalEffectResult.Proceed]) for purposes with no terminal effect.
     */
    suspend fun applyTerminalEffect(session: OnGoingInteractiveFlowSession): TerminalEffectResult =
        TerminalEffectResult.Proceed
}
