package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "A validation code this server sent to the end-user."
)
@Serdeable
data class ValidationCodeResource(
    @get:Schema(
        description = "Identifier of the validation code.",
        example = "c6e88c2c-6a31-4478-b6f0-3a3ef981eb2b"
    )
    val id: String,
    @get:Schema(
        description = "Media the validation code was sent through.",
        allowableValues = ["EMAIL", "SMS"],
        example = "EMAIL"
    )
    val media: String,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Reasons the validation code was sent: `EMAIL_CLAIM` confirms the end-user receives mail " +
                "at the value of their `email` claim, and `PHONE_NUMBER_CLAIM` that they receive messages at the " +
                "value of their `phone_number` claim."
        ),
        schema = Schema(allowableValues = ["EMAIL_CLAIM", "PHONE_NUMBER_CLAIM"])
    )
    val reasons: List<String>,
    @get:Schema(
        description = "Date and time at which a new validation code can be asked for, in UTC. Absent when " +
            "`advanced.validation-code.resend-delay` is not configured, in which case a new one can be asked " +
            "for only once this one has expired.",
        example = "2026-01-01T10:43:30"
    )
    val resendDate: LocalDateTime?
)
