package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Information about a configured claim."
)
@Serdeable
data class AdminClaimResource(
    val id: String,
    val type: String,
    val standard: Boolean,
    val enabled: Boolean,
    val required: Boolean,
    val identifier: Boolean,
    @get:JsonProperty("allowed_values")
    val allowedValues: List<Any>?,
    val group: String?
)
