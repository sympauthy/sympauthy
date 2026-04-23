package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Information about a configured claim."
)
@Serdeable
data class AdminClaimResource(
    @get:Schema(description = "Unique claim identifier, as defined in configuration.")
    val id: String,
    @get:Schema(description = "Data type expected for this claim.", allowableValues = ["string", "number", "date"])
    val type: String,
    @get:Schema(description = "Where the claim is defined.", allowableValues = ["openid", "custom"])
    val origin: String,
    @get:Schema(description = "Whether collection is enabled for this claim.")
    val enabled: Boolean,
    @get:Schema(description = "Whether the end-user must provide this claim to complete an authorization flow.")
    val required: Boolean,
    @get:Schema(description = "Whether this claim is configured as an identifier claim, used for password login and cross-provider account merging.")
    val identifier: Boolean,
    @get:Schema(
        description = "Array of accepted values, or null if any value is accepted (no restriction).",
        nullable = true
    )
    @get:JsonProperty("allowed_values")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val allowedValues: List<Any>?,
    @get:Schema(
        description = "Grouping identifier (e.g. \"profile\", \"address\"), or null if the claim belongs to no group.",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val group: String?
)
