package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "Information about a provider linked to a user."
)
@Serdeable
data class ClientProviderResource(
    @get:Schema(description = "Identifier of the provider.")
    @get:JsonProperty("provider_id")
    val providerId: String,
    @get:Schema(description = "The user's subject identifier at this provider.")
    val subject: String,
    @get:Schema(description = "The date and time (in UTC timezone) at which the provider was linked.")
    @get:JsonProperty("linked_at")
    val linkedAt: LocalDateTime
)
