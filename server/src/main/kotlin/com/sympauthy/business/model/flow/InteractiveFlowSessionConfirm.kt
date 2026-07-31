package com.sympauthy.business.model.flow

import java.time.LocalDateTime
import java.util.*

/**
 * The parameter of an [InteractiveFlowPurpose.CONFIRM] gate: what the signed-in end-user is being asked to
 * approve and who initiated it, attached to an [InteractiveFlowSession].
 *
 * Keyed by [sessionId] and fetched via
 * [com.sympauthy.business.manager.flow.confirm.InteractiveFlowSessionConfirmManager], never carried on the
 * session itself. Stored when a client-initiated flow prepends the [InteractiveFlowPurpose.CONFIRM] gate.
 */
data class InteractiveFlowSessionConfirm(
    /**
     * Identifier of the [InteractiveFlowSession] this confirm parameter is attached to.
     */
    val sessionId: UUID,

    /**
     * The action the end-user is asked to approve.
     */
    val action: ConfirmActionType,

    /**
     * The identifier of the client that initiated the action on the end-user's behalf, or null when the action
     * was initiated by an administrator.
     */
    val clientId: String? = null,

    /**
     * When the end-user approved the action, or null while the confirmation is still pending. Acts as the
     * resolved marker for the [InteractiveFlowPurpose.CONFIRM] purpose.
     */
    val confirmedDate: LocalDateTime? = null,
) {
    /**
     * Whether the end-user has approved the action.
     */
    val confirmed: Boolean get() = confirmedDate != null
}
