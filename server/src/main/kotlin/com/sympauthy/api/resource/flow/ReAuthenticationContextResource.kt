package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Context the sign-in page needs to render the re-authentication banner while the end-user is being asked to prove
ownership of an existing account (e.g. to attach a third-party provider to it).
    """
)
@Serdeable
data class ReAuthenticationContextResource(
    @get:Schema(description = "Reason the re-authentication is required, e.g. PROVIDER_ATTACH.")
    val purpose: String,
    @get:Schema(description = "The third-party provider being attached to the existing account.")
    val provider: ReAuthenticationProviderResource,
    @get:Schema(
        description = "Identifier claim values (e.g. email) of the existing account the end-user must sign in as."
    )
    val identifiers: Map<String, String>,
    @get:Schema(description = "Credentials the existing account can re-authenticate with (e.g. PASSWORD, PROVIDER).")
    @get:JsonProperty("available_methods")
    val availableMethods: List<String>
)

@Serdeable
data class ReAuthenticationProviderResource(
    val id: String,
    val name: String
)
