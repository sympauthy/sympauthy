package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "The client an interactive flow session was started for."
)
@Serdeable
data class ClientReferenceResource(
    @get:Schema(description = "Identifier of the client.")
    @get:JsonProperty("id")
    val id: String,
    @get:Schema(
        name = "display_name",
        description = "Name the configuration gives the client, in every language. Absent when it gives none, " +
            "and the page then shows the identifier."
    )
    @get:JsonProperty("display_name")
    val displayName: String?
)
