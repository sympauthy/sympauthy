package com.sympauthy.business.model.flow

/**
 * How the end-user must be handed back to the flow's initiator once an [InteractiveFlowSession] reaches a
 * terminal status.
 *
 * This tags the terminal redirect target carried on the session ([InteractiveFlowSession] success/cancel
 * redirect URIs) so the transport can build the final redirect without knowing which purpose initiated the
 * session.
 */
enum class InteractiveFlowRedirectType {
    /**
     * OAuth2 authorization-code style: on completion, mint an authorization code and append it (with the
     * client's echoed `state`) to the success redirect URI; on cancellation, return the OAuth2
     * `error=access_denied` response to the redirect URI (no code minted).
     */
    AUTHORIZATION_CODE,

    /**
     * Plain redirect: the end-user is redirected to the success (or cancellation) URI verbatim, with no
     * authorization code or OAuth2 error appended.
     */
    PLAIN,
}
