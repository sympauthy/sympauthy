package com.sympauthy.client.authorization.webhook.model

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class AuthorizationWebhookResponse(
    val scopes: Map<String, String>
)
