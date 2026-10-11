package com.sympauthy.api.resource

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Version of this server and of the API it serves.")
@Serdeable
data class VersionResource(
    @get:Schema(description = "Version of this server.")
    @get:JsonProperty("version")
    val version: String,
    @get:Schema(description = "Versions of the API this server serves, as this OpenAPI document numbers them.")
    @get:JsonProperty("api_versions")
    val apiVersions: List<String>
)
