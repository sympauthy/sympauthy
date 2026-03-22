package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(
    description = "Claims associated with a user."
)
@Serdeable
data class ClientUserClaimResource(
    @get:Schema(description = "Unique identifier of the user.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(description = "Claims associated with the user. Keys are claim identifiers, values are claim values.")
    val claims: Map<String, Any?>
)
