package com.sympauthy.client.accessreview.webhook.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class AccessReviewWebhookResponse(
    /**
     * `allow`, `deny` or `revoke_session`. Any other value is a failure rather than an allow.
     */
    val decision: String
)
