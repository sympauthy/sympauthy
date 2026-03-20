package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Information about a configured scope."
)
@Serdeable
data class AdminScopeResource(
    @get:Schema(description = "Unique scope identifier.")
    val id: String,
    @get:Schema(
        description = "Scope type.",
        allowableValues = ["consentable", "grantable", "client"]
    )
    val type: String,
    @get:Schema(
        description = "Where the scope is defined.",
        allowableValues = ["openid", "system", "custom"]
    )
    val origin: String,
    @get:Schema(description = "Whether the scope is enabled.")
    val enabled: Boolean,
    @get:Schema(
        description = "Array of claim identifiers protected by this scope. Only present for consentable scopes.",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val claims: List<String>?
)
