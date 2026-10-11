package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A provider the end-user can sign in with."
)
@Serdeable
data class ProviderResource(
    @get:Schema(
        description = "Identifier of the provider."
    )
    @get:JsonProperty("id")
    val id: String,
    @get:Schema(
        description = "Name of the provider to show the end-user."
    )
    @get:JsonProperty("name")
    val name: String,
    @get:Schema(
        name = "authorize_url",
        description = "URL that starts signing in with the provider, redirecting the browser to its " +
            "authorization page."
    )
    @get:JsonProperty("authorize_url")
    val authorizeUrl: String
)
