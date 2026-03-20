package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Information about a client."
)
@Serdeable
data class AdminClientResource(
    @get:Schema(description = "Unique identifier of the client, as defined in configuration.")
    @get:JsonProperty("client_id")
    val clientId: String,
    @get:Schema(
        description = "Type of the client as defined by the OAuth 2.1 specification.",
        allowableValues = ["public", "confidential"]
    )
    val type: String,
    @get:Schema(description = "Scopes the client is allowed to request.")
    @get:JsonProperty("allowed_scopes")
    val allowedScopes: List<String>,
    @get:Schema(description = "Scopes granted by default when the client does not explicitly request any.")
    @get:JsonProperty("default_scopes")
    val defaultScopes: List<String>,
    @get:Schema(description = "Redirect URIs the client is allowed to use during authorization.")
    @get:JsonProperty("allowed_redirect_uris")
    val allowedRedirectUris: List<String>
)
