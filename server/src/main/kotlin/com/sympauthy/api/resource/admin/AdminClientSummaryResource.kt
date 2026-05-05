package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Summary information about a client."
)
@Serdeable
data class AdminClientSummaryResource(
    @get:Schema(description = "Unique identifier of the client, as defined in configuration.")
    @get:JsonProperty("client_id")
    val clientId: String,
    @get:Schema(
        description = "Type of the client as defined by the OAuth 2.1 specification.",
        allowableValues = ["public", "confidential"]
    )
    val type: String,
    @get:Schema(description = "Identifier of the audience this client belongs to.")
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(description = "Redirect URIs the client is allowed to use during authorization.")
    @get:JsonProperty("allowed_redirect_uris")
    val allowedRedirectUris: List<String>
)
