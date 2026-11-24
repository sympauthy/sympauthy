package com.sympauthy.business.flow.model

/**
 * The outcome of resolving the current step of an [InteractiveFlowSession].
 *
 * Pairs the [step] to present with the [session] as it stands after any completion transition performed
 * while resolving it (an ongoing session may become completed or failed).
 */
data class InteractiveFlowStepResult(
    val session: InteractiveFlowSession,
    val step: InteractiveFlowStep
)
