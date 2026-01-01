package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "Information about a validation code send to the end-user by this authorization server."
)
@Serdeable
data class ValidationCodeResource(
    @get:Schema(
        description = "Unique identifier of the code.",
        example = "c6e88c2c-6a31-4478-b6f0-3a3ef981eb2b"
    )
    val id: String,
    @get:Schema(
        description = "The media used to send the code to the end-user.",
        example = "MAIL"
    )
    val media: String,
    @get:Schema(
        description = """
The list of reasons this code was send to the end-user.


- ```EMAIL_CLAIM```: Verify if the end-user has access to the email box he is pretending to own.
- ```RESET_PASSWORD```: Verify the end-user has access to the email box associated to an existing account before 
  allowing him to reset its password.
"""
    )
    val reasons: List<String>,
    @get:Schema(
        description = "The date and time (in UTC timezone) at which a new validation code can be issued to the end-user.",
        example = "2026-01-01T10:43:30"
    )
    val resendDate: LocalDateTime?
)
