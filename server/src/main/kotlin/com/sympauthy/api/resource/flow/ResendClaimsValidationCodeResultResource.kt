package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
@Schema(
    description = """
Result of the resent operation on a claim validation code.
"""
)
data class ResendClaimsValidationCodeResultResource(

    @get:Schema(
        description = "The media for which a new validation code has been asked.",
    )
    val media: String,

    @get:Schema(
        description = """
Indicates whether this authorization server has sent a new validation code to the end-user or not.
        """
    )
    val resent: Boolean,

    @get:Schema(
        description = """
Information about the new validation code that has been sent to the end-user. Empty or absent if resent is false. 
"""
    )
    val code: ValidationCodeResource? = null,
)
