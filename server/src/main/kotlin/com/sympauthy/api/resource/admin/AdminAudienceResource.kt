package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Information about a configured audience."
)
@Serdeable
data class AdminAudienceResource(
    @get:Schema(
        description = "Unique identifier of the audience, as defined in the YAML configuration key."
    )
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(
        description = "Value used as the aud claim in access and refresh tokens issued for clients belonging to this audience. Defaults to the audience identifier when not explicitly configured."
    )
    @get:JsonProperty("token_audience")
    val tokenAudience: String
)
