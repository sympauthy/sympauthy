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
     * The end-user must enroll a multi-factor authentication method (TOTP). Inserted right after another
     * purpose (e.g. [OAUTH2_AUTHORIZE]) that requires MFA and whose user is not yet enrolled, or run standalone
     * from a client-initiated enrollment.
     */
    MFA_ENROLLMENT,

    /**
     * The end-user must pass a multi-factor authentication challenge (TOTP) with an already-enrolled method.
     * Inserted right after another purpose (e.g. [OAUTH2_AUTHORIZE]) that requires MFA and whose user is
     * enrolled.
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
     * [MFA_CHALLENGE] is inserted right after it so re-authentication is at least as strong as the account's
     * own login.
     *
     * Prepended in front of a sensitive purpose by a consumer (e.g. provider-link); progress is tracked by the
     * session's attached re-authentication record ([InteractiveFlowSessionReauthentication]).
     */
    REAUTHENTICATION,

    /**
     * Link a third-party identity provider to the end-user's **already-fixed** account
     * ([InteractiveFlowSession.userId] is set before this purpose runs). The end-user authorizes with the
     * target provider — its id is stored on the session's attached link record
     * ([InteractiveFlowSessionLinkProvider]) — and on the provider callback the resolved subject is linked to
     * the fixed user, subject to hard-fail conflict checks (the subject, or an identifier claim, already owned
     * by another account).
     *
     * Always gated by a [REAUTHENTICATION] step (and, for a client/admin-initiated link, driven behind a
     * [CONFIRM] gate): linking a provider mints a durable login credential, so the browser must prove it
     * genuinely owns the account before the link commits.
     */
    LINK_PROVIDER,

    /**
     * A gate the end-user adds in front of [OAUTH2_AUTHORIZE] from the sign-in step, to reset a password they
     * forgot rather than sign in with it. Prepended when the end-user asks for it, which is a choice of theirs
     * rather than something the session or the configuration implies.
     *
     * The end-user types an identifier, then the code sent to the proven email address of the account holding
     * it, then a new password. The code establishes [InteractiveFlowSession.userId] as a password sign-in does,
     * so the authorization then continues as after one, a second factor included. The new password replaces the
     * account's others, and the grants they opened are revoked, only once the session completes. A session
     * whose user is established otherwise, by a sign-in before any code was proven, leaves it resolved with
     * nothing to apply.
     *
     * Its progress is the session's attached password reset record ([InteractiveFlowSessionPasswordReset]); it
     * owns no hand-off and is never a session's [InteractiveFlowSession.initiatingPurpose].
     */
    PASSWORD_RESET
}
