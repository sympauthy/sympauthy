package com.sympauthy.business.model.flow

/**
 * The purpose an [InteractiveFlowSession] serves.
 *
 * Selects the handler that owns the session's step state machine and completion behaviour.
 */
enum class FlowPurpose {
    /**
     * The session backs the OAuth2 / OpenID Connect authorization flow initiated by a client at
     * `/api/oauth2/authorize`.
     */
    OAUTH2_AUTHORIZE
}
