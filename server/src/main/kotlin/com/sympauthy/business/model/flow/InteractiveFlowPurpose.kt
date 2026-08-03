package com.sympauthy.business.model.flow

/**
 * A purpose an [InteractiveFlowSession] serves.
 *
 * A session carries an ordered list of purposes; each value selects the handler that owns that purpose's step
 * state machine, and — for the session's initiating purpose — its terminal handoff.
 */
enum class InteractiveFlowPurpose {
    /**
     * A gate the signed-in end-user must clear before the rest of the session runs: they must explicitly
     * approve an action a client (or an administrator) initiated on their behalf. Prepended in front of the
     * initiating purpose (e.g. [MFA_ENROLLMENT]) so it is driven first; it owns no terminal handoff and is
     * never a session's [InteractiveFlowSession.initiatingPurpose]. What is being confirmed is described by
     * the session's attached confirm record ([InteractiveFlowSessionConfirm]).
     */
    CONFIRM,

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
    MFA_CHALLENGE,

    /**
     * A gate that makes the end-user prove they control the account they are **already** identified as
     * ([InteractiveFlowSession.userId] is fixed before this purpose runs), before a sensitive step proceeds.
     *
     * It reuses the sign-in step ([InteractiveFlowStep.SignIn]) but with **confirm, never establish** semantics:
     * the authenticated credential must resolve to the pre-set [InteractiveFlowSession.userId] (fail on
     * mismatch); the session's user is never switched. Only the credentials the account already has are offered
     * (password if set, providers already linked to the user). When the account is MFA-enrolled, a fresh
     * [MFA_CHALLENGE] is appended so re-authentication is at least as strong as the account's own login.
     *
     * Prepended in front of a sensitive purpose by a consumer (e.g. provider-link); progress is tracked by the
     * session's attached re-authentication record ([InteractiveFlowSessionReauthentication]).
     */
    REAUTHENTICATION
}
