package com.sympauthy.business.model.flow

import java.time.LocalDateTime
import java.util.*

/**
 * The progress of an [InteractiveFlowPurpose.REAUTHENTICATION] gate: how far the end-user has gone in proving
 * they control the already-fixed [InteractiveFlowSession.userId], attached to an [InteractiveFlowSession].
 *
 * Keyed by [sessionId] and fetched via
 * [com.sympauthy.business.manager.flow.reauth.InteractiveFlowSessionReauthenticationManager], never carried on
 * the session itself. Deliberately its own record rather than the shared [InteractiveFlowSession.userId] (which
 * is already set) or [OnGoingInteractiveFlowSession.mfaPassed] (the MFA second factor runs as a separate
 * appended [InteractiveFlowPurpose.MFA_CHALLENGE]).
 */
data class InteractiveFlowSessionReauthentication(
    /**
     * Identifier of the [InteractiveFlowSession] this re-authentication progress is attached to.
     */
    val sessionId: UUID,

    /**
     * When the end-user proved the account's primary credential (a password or an already-linked provider), or
     * null while it is still pending. Acts as the resolved marker for the
     * [InteractiveFlowPurpose.REAUTHENTICATION] purpose (the required MFA second factor, when the account is
     * enrolled, is handled by a separate appended [InteractiveFlowPurpose.MFA_CHALLENGE]).
     */
    val primaryCredentialProvenDate: LocalDateTime? = null,
) {
    /**
     * Whether the end-user has proven the account's primary credential.
     */
    val primaryCredentialProven: Boolean get() = primaryCredentialProvenDate != null
}
