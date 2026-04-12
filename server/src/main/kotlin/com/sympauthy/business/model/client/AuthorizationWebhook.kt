package com.sympauthy.business.model.client

import java.net.URI

/**
 * Configuration for delegating authorization decisions to an external HTTP server via webhook.
 *
 * When configured on a client, the authorization server will call the webhook endpoint
 * to determine which scopes to grant or deny during the authorization flow.
 */
data class AuthorizationWebhook(
    /**
     * The URL of the external server's webhook endpoint.
     */
    val url: URI,
    /**
     * The HMAC-SHA256 signing key used to sign the request body.
     * The signature is sent in the `X-SympAuthy-Signature` header.
     */
    val secret: String,
    /**
     * The behavior to adopt when the webhook call fails (network error, timeout, non-2xx response).
     */
    val onFailure: AuthorizationWebhookOnFailure,
)

enum class AuthorizationWebhookOnFailure {
    /**
     * Deny all requested scopes if the webhook call fails.
     */
    DENY_ALL,

    /**
     * Fall back to the standard scope granting rules if the webhook call fails.
     */
    FALLBACK_TO_RULES
}
