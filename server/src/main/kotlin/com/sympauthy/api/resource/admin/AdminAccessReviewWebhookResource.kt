package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Webhook configuration for reviewing where a token is being validated from."
)
@Serdeable
data class AdminAccessReviewWebhookResource(
    @get:Schema(description = "URL of the webhook endpoint.")
    val url: String,
    @get:Schema(
        description = "What makes the webhook worth calling.",
        allowableValues = ["new_context", "every_validation"]
    )
    val on: String,
    @get:Schema(
        description = "Behavior when the webhook call fails.",
        allowableValues = ["deny", "allow"]
    )
    @get:JsonProperty("on_failure")
    val onFailure: String
)
