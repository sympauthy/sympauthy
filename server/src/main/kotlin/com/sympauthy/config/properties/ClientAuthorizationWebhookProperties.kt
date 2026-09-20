package com.sympauthy.config.properties

/**
 * Shared interface for the authorization webhook a client, or the template it takes its defaults from,
 * configures.
 *
 * It carries no `@ConfigurationProperties` of its own. That annotation anchors a nested class to the
 * prefix of the class it is nested in, so a single annotated interface nested in one owner and reused
 * by the other binds both to the first owner's prefix — which is how
 * `templates.clients.*.webhooks.authorization` came to be a key nothing read. Each owner nests its own
 * annotated twin extending this one, so a client and a template declare the same fields without either
 * of them repeating the other.
 */
interface ClientAuthorizationWebhookProperties {
    val url: String?
    val secret: String?
    val onFailure: String?
}
