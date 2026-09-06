package com.sympauthy.config.properties

/**
 * Shared interface for the access-review webhook a client, or the template it takes its defaults from,
 * configures.
 *
 * It carries no `@ConfigurationProperties` of its own, for the reason
 * [ClientAuthorizationWebhookProperties] gives: each owner nests its own annotated twin extending this
 * one, so that neither binds under the other's prefix.
 */
interface ClientAccessReviewWebhookProperties {
    val url: String?
    val secret: String?
    val on: String?
    val onFailure: String?
}
