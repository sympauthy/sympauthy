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
        description = """
List of claims that uniquely identify a user, usable as login for sign-in.
Only set for the sign-in step; during sign-up the identifier claims are exposed in the ```claims``` field instead.
        """
    )
    @get:JsonProperty("identifier_claims")
    val identifierClaims: List<String>? = null,
)
