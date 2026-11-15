package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Serdeable
@Schema(
    description = "Outcome of asking for a new code to reset a password. It is the same whether or not a user " +
        "holds the login, and whether or not a code was sent."
)
data class ResendResetPasswordCodeResultResource(
    @get:Schema(
        description = "Whether a new code was asked for."
    )
    @get:JsonProperty("resent")
    val resent: Boolean,
    @get:Schema(
        name = "resend_date",
        description = "Date and time at which a new code can be asked for, in UTC. Absent when " +
            "`advanced.validation-code.resend-delay` is not configured, and when the interactive flow session " +
            "holds no reset.",
        example = "2026-01-01T10:43:30"
    )
    @get:JsonProperty("resend_date")
    val resendDate: LocalDateTime? = null
)
