package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Validation code the end-user entered."
)
@Serdeable
data class ClaimValidationInputResource(
    @get:Schema(
        description = "Media the end-user received the validation code through.",
        allowableValues = ["EMAIL", "SMS"]
    )
    val media: String,
    @get:Schema(
        description = "Validation code the end-user entered."
    )
    val code: String,
)
