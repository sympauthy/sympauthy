package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Configuration of signing in or signing up with a password."
)
@Serdeable
data class PasswordResource(
    @get:Schema(
        name = "identifier_claims",
        description = "Identifier claims: the value of any one of them is a login at sign-in, and sign-up asks " +
            "for all of them."
    )
    @get:JsonProperty("identifier_claims")
    val identifierClaims: List<CollectableClaimResource>,
)
