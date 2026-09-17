package com.sympauthy.business.model.flow

/**
 * Where one purpose of a session stands, and what its handler has to say about it.
 *
 * All three parts are derived rather than stored: [status] from the session's completed-purpose list and the
 * engine's read-only walk, and [debugInformation] from the handler that owns [purpose].
 *
 * It does not say whether [purpose] is the one that initiated the session.
 * [InteractiveFlowSession.initiatingPurpose] already answers that, and repeating it here would be the same
 * fact written twice — which somebody has to reconcile the first time the two disagree.
 */
data class InteractiveFlowPurposeProgress(
    val purpose: InteractiveFlowPurpose,
    val status: InteractiveFlowPurposeStatus,
    val debugInformation: List<PurposeDebugInformation>
)

/**
 * Where a purpose stands within the session carrying it.
 */
enum class InteractiveFlowPurposeStatus {
    /** The engine has recorded this purpose as resolved. */
    COMPLETED,

    /**
     * The first purpose still needing a step, which is the one the end-user is stopped at. A terminal session
     * has none; a session that was ongoing when it expired still does, and that is the whole question an
     * operator opens it with.
     */
    CURRENT,

    /** Neither resolved nor driving: a purpose the session has not reached. */
    PENDING
}
