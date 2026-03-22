package com.sympauthy.api.resource.client

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Request to update custom claims for a user. Only custom claims can be modified."
)
@Serdeable
data class ClientUserClaimUpdateResource(
    @get:Schema(description = "Claims to update. Keys are custom claim identifiers, values are the new claim values.")
    val claims: Map<String, Any?>
)
