package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(
    description = "Result of a provider unlink operation."
)
@Serdeable
data class AdminUserProviderUnlinkResource(
    @get:Schema(description = "Unique identifier of the user.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(description = "Identifier of the unlinked provider.")
    @get:JsonProperty("provider_id")
    val providerId: String,
    @get:Schema(description = "Whether the provider was successfully unlinked.")
    val unlinked: Boolean
)
