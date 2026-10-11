package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A scope this server knows, as configured."
)
@Serdeable
data class AdminScopeResource(
    @get:Schema(description = "Identifier of the scope, as a client requests it.")
    val id: String,
    @get:Schema(
        description = "How a token comes to carry the scope. `consentable`: the end-user consents to it. " +
                "`grantable`: this server grants it, by its scope granting rules or automatically. `client`: a " +
                "client obtains it for itself with the `client_credentials` grant.",
        allowableValues = ["consentable", "grantable", "client"]
    )
    val type: String,
    @get:Schema(
        description = "Where the scope is defined: by OpenID Connect for `openid`, by this server for its admin " +
                "and client APIs for `system`, by the configuration for `custom`.",
        allowableValues = ["openid", "system", "custom"]
    )
    val origin: String,
    @get:Schema(
        description = "Whether this server serves the scope. A scope the configuration turned off is listed " +
                "here and nowhere else, and no token carries it."
    )
    val enabled: Boolean,
    @get:Schema(
        description = "Identifiers of the claims this scope protects. Null unless `type` is `consentable`.",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val claims: List<String>?
)
