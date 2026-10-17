package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
@Schema(
    description = "Outcome of asking for a new validation code."
)
data class ResendClaimsValidationCodeResultResource(

    @get:Schema(
        description = "Media a new validation code was asked for.",
        allowableValues = ["email", "sms"]
    )
    val media: String,

    @get:Schema(
        description = "Whether a new validation code was sent."
    )
    val resent: Boolean,

    @get:Schema(
        description = "New validation code sent to the end-user. Absent when `resent` is `false`."
    )
    val code: ValidationCodeResource? = null,
)
