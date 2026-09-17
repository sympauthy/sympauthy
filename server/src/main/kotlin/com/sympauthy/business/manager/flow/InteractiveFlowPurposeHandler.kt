package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
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

    /**
     * What this purpose has to say about where [session] stands, for an operator reading the session on the
     * admin API. Each entry is a label and the value behind it, in the order the operator reads them.
     *
     * **Required rather than defaulted to nothing**, so that adding a purpose does not compile until somebody
     * has decided what an operator should see when it stalls. A purpose that shipped contributing nothing here
     * would be discovered by the person debugging that exact purpose, at the worst possible moment.
     *
     * It takes the base [InteractiveFlowSession] rather than an ongoing one, unlike every other member: the
     * most valuable session to describe is a failed one, and a handler that could only describe an ongoing
     * session would go quiet exactly when it is needed. Every attached-record read it needs already takes the
     * base type.
     *
     * **It must answer for a session in any shape** — no user, no attached record, a terminal status — and
     * emit the label with no value rather than throwing. This is called when a session is malformed, and
     * nothing catches around it: a handler that threw would take down the one page an operator has, and a
     * backstop that swallowed it would make a handler that cannot describe a session look like one with
     * nothing to say. Its own test is what holds the rule.
     *
     * **It emits what an operator can act on, and never a credential.** A TOTP seed, a recovery code and an
     * authorization code are never among the entries; a value that exists so that only the client and the
     * browser hold it — `state`, `nonce` — is reported as present or absent rather than published.
     */
    suspend fun debugInformation(session: InteractiveFlowSession): List<PurposeDebugInformation>
}
