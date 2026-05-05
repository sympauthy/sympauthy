package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Webhook configuration for delegating authorization decisions."
)
@Serdeable
data class AdminAuthorizationWebhookResource(
    @get:Schema(description = "URL of the webhook endpoint.")
    val url: String,
    @get:Schema(
        description = "Behavior when the webhook call fails.",
        allowableValues = ["deny_all", "fallback_to_rules"]
    )
    @get:JsonProperty("on_failure")
    val onFailure: String
)
