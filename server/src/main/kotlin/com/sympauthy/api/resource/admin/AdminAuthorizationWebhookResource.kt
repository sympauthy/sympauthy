package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Webhook this server calls during an authorization to decide which scopes to grant, before " +
            "its scope granting rules."
)
@Serdeable
data class AdminAuthorizationWebhookResource(
    @get:Schema(description = "URL this server calls.")
    val url: String,
    @get:Schema(
        description = "What this server does when the call fails, for example on a timeout or an error response. " +
                "`deny_all` denies every requested scope; `fallback_to_rules` applies the scope granting " +
                "rules instead.",
        allowableValues = ["deny_all", "fallback_to_rules"]
    )
    @get:JsonProperty("on_failure")
    val onFailure: String
)
