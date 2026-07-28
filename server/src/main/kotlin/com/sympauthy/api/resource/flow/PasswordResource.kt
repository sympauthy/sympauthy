package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
If null or not present, the associated password authentication method is disabled by the authorization server.
"""
)
@Serdeable
data class PasswordResource(
    @get:Schema(
        name = "identifier_claims",
        description = "List of claims that uniquely identify a user. Used as login for sign-in and as required claims for sign-up."
    )
    @get:JsonProperty("identifier_claims")
    val identifierClaims: List<String>,
)
