package com.sympauthy.business.model.flow

/**
 * A purpose an [InteractiveFlowSession] serves.
 *
 * A session carries an ordered list of purposes; each value selects the handler that owns that purpose's step
 * state machine, and — for the session's initiating purpose — its terminal handoff.
 */
enum class InteractiveFlowPurpose {
    /**
     * The session backs the OAuth2 / OpenID Connect authorization flow initiated by a client at
     * `/api/oauth2/authorize`.
     */
    OAUTH2_AUTHORIZE,

    /**
     * The end-user must enroll a multi-factor authentication method (TOTP). Appended to another purpose
     * (e.g. [OAUTH2_AUTHORIZE]) that requires MFA and whose user is not yet enrolled, or run standalone from
     * a client-initiated enrollment.
     */
    MFA_ENROLLMENT,

    /**
     * The end-user must pass a multi-factor authentication challenge (TOTP) with an already-enrolled method.
     * Appended to another purpose (e.g. [OAUTH2_AUTHORIZE]) that requires MFA and whose user is enrolled.
     */
    MFA_CHALLENGE
}
