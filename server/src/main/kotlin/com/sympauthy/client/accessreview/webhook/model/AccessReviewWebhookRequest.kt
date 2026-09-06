package com.sympauthy.client.accessreview.webhook.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

/**
 * What a client is handed to decide on: who is asking, what they are asking for, the place they are
 * asking from, and the places they have been seen in before.
 */
@Serdeable
data class AccessReviewWebhookRequest(
    @get:JsonProperty("user_id")
    val userId: String,
    @get:JsonProperty("client_id")
    val clientId: String,
    /**
     * What the request being reviewed is: `userinfo` or `refresh_token`.
     */
    val reason: String,
    val current: AccessReviewWebhookContext,
    /**
     * The places this user has been seen in before, most recent first, bounded by
     * `advanced.webhooks.access-review.past-contexts`.
     */
    val past: List<AccessReviewWebhookContext>
)

/**
 * One place, as a client is shown it. The signing key and the fingerprint are this server's own and
 * are not published.
 */
@Serdeable
data class AccessReviewWebhookContext(
    val ip: String?,
    @get:JsonProperty("user_agent")
    val userAgent: String?,
    val country: String?,
    val region: String?,
    val city: String?,
    @get:JsonProperty("first_seen_date")
    val firstSeenDate: String,
    @get:JsonProperty("last_seen_date")
    val lastSeenDate: String,
    @get:JsonProperty("observation_count")
    val observationCount: Int,
    /**
     * True where the request being reviewed is the first this server has seen from this place.
     */
    val new: Boolean
)
