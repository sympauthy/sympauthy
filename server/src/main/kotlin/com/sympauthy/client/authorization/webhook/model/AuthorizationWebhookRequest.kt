package com.sympauthy.client.authorization.webhook.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class AuthorizationWebhookRequest(
    @get:JsonProperty("user_id")
    val userId: String,
    @get:JsonProperty("client_id")
    val clientId: String,
    @get:JsonProperty("requested_scopes")
    val requestedScopes: List<String>,
    val claims: Map<String, Any?>
)
