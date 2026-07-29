package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.InteractiveFlowSession

/**
 * Owns the step state machine and the completion behaviour of interactive flow sessions serving a given
 * [purpose].
 *
 * There is one implementation per [InteractiveFlowPurpose]; the one responsible for a session is resolved via
 * [InteractiveFlowPurposeRegistry]. The handler is the single authority on "given this session, what does the
 * end-user do next" — expressed as an abstract, transport-agnostic step — and on what completing the
 * flow means for that purpose.
 */
interface InteractiveFlowPurposeHandler {

    /**
     * The purpose this handler is responsible for.
     */
    val purpose: InteractiveFlowPurpose

    /**
     * Compute the next [com.sympauthy.business.model.flow.InteractiveFlowStep] for [session].
     *
     * When — and only when — the session has satisfied every step it requires, the completion effect is
     * run, transitioning the ongoing session to completed (or failed). The call is idempotent for a
     * session that is already terminal.
     *
     * Returns the possibly-transitioned session together with the step to present. The step is abstract:
     * mapping it to a concrete redirect (web) or another transport happens at the API boundary.
     */
    suspend fun getCurrentStep(session: InteractiveFlowSession): InteractiveFlowStepResult
}
