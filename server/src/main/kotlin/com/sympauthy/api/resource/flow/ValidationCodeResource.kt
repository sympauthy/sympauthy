package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
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
        name = "id",
        description = "Identifier of the validation code.",
        example = "c6e88c2c-6a31-4478-b6f0-3a3ef981eb2b"
    )
    @get:JsonProperty("id")
    val id: String,
    @get:Schema(
        name = "media",
        description = "Media the validation code was sent through.",
        allowableValues = ["email", "sms"],
        example = "email"
    )
    @get:JsonProperty("media")
    val media: String,
    @get:ArraySchema(
        arraySchema = Schema(
            name = "reasons",
            description = "Reasons the validation code was sent: `email_claim` confirms the end-user receives mail " +
                "at the value of their `email` claim, and `phone_number_claim` that they receive messages at the " +
                "value of their `phone_number` claim."
        ),
        schema = Schema(allowableValues = ["email_claim", "phone_number_claim"])
    )
    @get:JsonProperty("reasons")
    val reasons: List<String>,
    @get:Schema(
        name = "resend_date",
        description = "Date and time at which a new validation code can be asked for, in UTC. Absent when " +
            "`advanced.validation-code.resend-delay` is not configured, in which case a new one can be asked " +
            "for only once this one has expired.",
        example = "2026-01-01T10:43:30"
    )
    @get:JsonProperty("resend_date")
    val resendDate: LocalDateTime?
)
