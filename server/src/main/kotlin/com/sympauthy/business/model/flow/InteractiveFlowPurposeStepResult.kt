package com.sympauthy.business.model.flow

/**
 * The outcome of asking a [com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler] to advance a
 * single purpose of an ongoing session.
 *
 * A purpose either still requires the end-user to go through a [Pending] step, or is [Resolved] — every step
 * it requires has been satisfied. Both carry the [session] as it stands after the handler ran, since
 * resolving a purpose may mutate the session (e.g. appending follow-up purposes).
 */
sealed interface InteractiveFlowPurposeStepResult {

    val session: OnGoingInteractiveFlowSession

    /**
     * The purpose needs the end-user to go through [step] before it can resolve.
     */
    data class Pending(
        override val session: OnGoingInteractiveFlowSession,
        val step: InteractiveFlowStep
    ) : InteractiveFlowPurposeStepResult

    /**
     * The purpose has satisfied every step it requires; the engine moves on to the next purpose.
     */
    data class Resolved(
        override val session: OnGoingInteractiveFlowSession
    ) : InteractiveFlowPurposeStepResult
}
