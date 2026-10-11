package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "A provider linked to a user."
)
@Serdeable
data class AdminUserProviderResource(
    @get:Schema(description = "Identifier of the provider.")
    @get:JsonProperty("provider_id")
    val providerId: String,
    @get:Schema(description = "Identifier of the end-user at the provider: the subject the provider issued.")
    val subject: String,
    @get:Schema(description = "Date and time at which the provider was linked to the user, in UTC.")
    @get:JsonProperty("linked_at")
    val linkedAt: LocalDateTime
)
