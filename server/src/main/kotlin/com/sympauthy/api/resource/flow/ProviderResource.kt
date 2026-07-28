package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Configuration related to a third-party provider that can be used by the end-user to authenticate."
)
@Serdeable
data class ProviderResource(
    @get:Schema(
        description = "Identifier of the third-party provider."
    )
    @get:JsonProperty("id")
    val id: String,
    @get:Schema(
        description = "Name of the third-party provider as it should be displayed to the end-user."
    )
    @get:JsonProperty("name")
    val name: String,
    @get:Schema(
        name = "authorize_url",
        description = """
URL to redirect the end-user to to initiate a authorization grant flow with the third-party provider.
        """
    )
    @get:JsonProperty("authorize_url")
    val authorizeUrl: String
)
