package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
@Schema(
    description = "Request for a new validation code."
)
data class ResendClaimsValidationInputResource(
    @get:Schema(
        description = "Media to send a new validation code through.",
        allowableValues = ["EMAIL", "SMS"]
    )
    val media: String
)
