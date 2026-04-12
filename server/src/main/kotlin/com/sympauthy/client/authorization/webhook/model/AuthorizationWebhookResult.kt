package com.sympauthy.client.authorization.webhook.model

/**
 * Result of calling the authorization webhook endpoint.
 */
sealed class AuthorizationWebhookResult {

    /**
     * The webhook responded with a valid 2xx response containing scope decisions.
     */
    data class Success(
        val response: AuthorizationWebhookResponse
    ) : AuthorizationWebhookResult()

    /**
     * The webhook call failed due to a network error, timeout, non-2xx response, or invalid response payload.
     */
    data class Failure(
        val message: String,
        val cause: Exception? = null
    ) : AuthorizationWebhookResult()
}
