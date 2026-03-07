package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Information about a client."
)
@Serdeable
data class AdminClientResource(
    @get:JsonProperty("client_id")
    val clientId: String,
    @get:JsonProperty("allowed_scopes")
    val allowedScopes: List<String>,
    @get:JsonProperty("default_scopes")
    val defaultScopes: List<String>,
    @get:JsonProperty("allowed_redirect_uris")
    val allowedRedirectUris: List<String>
)
