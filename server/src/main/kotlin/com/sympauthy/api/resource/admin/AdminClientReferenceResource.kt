package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

internal const val CLIENT_DISPLAY_NAME_PROPERTY_DESCRIPTION =
        "Name the configuration gives the client, in every language. Absent when it gives none, and the client " +
        "is then shown by its identifier."

@Schema(
    description = "A client a record names, which the configuration may no longer declare."
)
@Serdeable
data class AdminClientReferenceResource(
    @get:Schema(description = "Identifier of the client.")
    @get:JsonProperty("id")
    val id: String,
    @get:Schema(
        description = CLIENT_DISPLAY_NAME_PROPERTY_DESCRIPTION + " Absent too when the configuration no longer " +
                "declares the client."
    )
    @get:JsonProperty("display_name")
    val displayName: String?
)
