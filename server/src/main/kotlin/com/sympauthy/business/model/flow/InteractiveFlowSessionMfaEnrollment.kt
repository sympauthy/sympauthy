package com.sympauthy.business.model.flow

import java.util.*

/**
 * The context of a standalone, client-initiated MFA enrollment attached to an [InteractiveFlowSession] whose
 * [InteractiveFlowSession.initiatingPurpose] is [InteractiveFlowPurpose.MFA_ENROLLMENT].
 *
 * Present only for an enrollment started outside an OAuth2 authorization (e.g. a signed-in user enrolling a
 * TOTP authenticator on demand). Keyed by [sessionId] and fetched via
 * [com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager], never carried on the
 * session itself.
 */
data class InteractiveFlowSessionMfaEnrollment(
    /**
     * Identifier of the [InteractiveFlowSession] this enrollment context is attached to.
     */
    val sessionId: UUID,

    /**
     * The URI the end-user is redirected back to once the enrollment completes. Validated against the
     * initiating client's registered redirect URIs when the enrollment is started.
     */
    val returnUri: String,
)
