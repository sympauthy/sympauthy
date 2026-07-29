package com.sympauthy.business.model.flow

/**
 * Discriminator for the purpose an [InteractiveFlowSession] serves.
 *
 * This is a minimal placeholder while the interactive flow is being decoupled from OAuth2. A richer
 * `FlowPurpose` (declaring required steps and a completion handler) will formalize this in a later
 * sub-task of the epic; for now the only supported purpose is the OAuth2 authorization flow.
 */
enum class InteractiveFlowSessionType {
    /**
     * The session backs the OAuth2 / OpenID Connect authorization flow initiated by a client.
     */
    OAUTH2
}
