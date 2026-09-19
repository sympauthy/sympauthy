package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.model.flow.InteractiveFlowStep

/**
 * The outcome of routing an MFA method-selection step (enrollment or challenge): either a single method the
 * end-user is auto-redirected to, or a choice of methods to render (optionally with a skip).
 */
sealed class MfaRoutingResult

/**
 * There is no real choice to make; the UI must follow [step] without rendering a selection screen.
 */
data class MfaAutoRedirect(val step: InteractiveFlowStep) : MfaRoutingResult()

/**
 * The UI must render a selection screen listing [methods]; [skippable] indicates whether a skip is offered.
 */
data class MfaMethodSelection(
    val methods: List<AvailableMfaMethod>,
    val skippable: Boolean
) : MfaRoutingResult()

/**
 * One MFA method the end-user may pick, and the [step] it leads to.
 */
data class AvailableMfaMethod(val name: String, val step: InteractiveFlowStep)
