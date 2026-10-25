package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(
    description = "Claims of a user this client may read."
)
@Serdeable
data class ClientUserClaimResource(
    @get:Schema(description = "Identifier of the user.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(
        description = "Values of the claims this client may read, including those this server generates such " +
            "as `sub`, keyed by claim identifier."
    )
    val claims: Map<String, Any?>
)
