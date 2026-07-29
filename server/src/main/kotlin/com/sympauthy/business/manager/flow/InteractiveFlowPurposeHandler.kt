package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStepResult
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession

/**
 * Owns the step state machine and the completion behaviour of a single [purpose] of an interactive flow
 * session.
 *
 * There is one implementation per [InteractiveFlowPurpose]; the one responsible for a purpose is resolved via
 * [InteractiveFlowPurposeRegistry]. A session may carry several purposes, sequenced by [InteractiveFlowEngine]:
 * the handler is the authority on "given this session, what does the end-user do next for my purpose" —
 * expressed as an abstract, transport-agnostic step — and, when it is the session's initiating purpose, on
 * what completing the whole flow means.
 */
interface InteractiveFlowPurposeHandler {

    /**
     * The purpose this handler is responsible for.
     */
    val purpose: InteractiveFlowPurpose

    /**
     * Compute the next step this purpose requires on the ongoing [session], or report the purpose
     * [InteractiveFlowPurposeStepResult.Resolved] once every step it requires is satisfied.
     *
     * As the effect of resolving, a handler may append follow-up purposes to the session (e.g. the OAuth2
     * authorize purpose appending an MFA purpose); the mutated session is returned in the result.
     *
     * The step is abstract: mapping it to a concrete redirect (web) or another transport happens at the API
     * boundary.
     */
    suspend fun getNextStep(session: OnGoingInteractiveFlowSession): InteractiveFlowPurposeStepResult

    /**
     * Run the terminal handoff of a session whose initiating purpose is [purpose] and whose every purpose has
     * resolved, transitioning the ongoing [session] to completed (or failed).
     *
     * Called by [InteractiveFlowEngine] only for the session's initiating purpose, and only once every
     * purpose has resolved.
     */
    suspend fun complete(session: OnGoingInteractiveFlowSession): InteractiveFlowSession
}
