package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Information about a configured claim."
)
@Serdeable
@Suppress("MaxLineLength")
data class AdminClaimResource(
    @get:Schema(description = "Unique claim identifier, as defined in configuration.")
    val id: String,
    @get:Schema(description = "Data type expected for this claim.", allowableValues = ["string", "number", "date"])
    val type: String,
    @get:Schema(description = "Where the claim is defined.", allowableValues = ["openid", "custom"])
    val origin: String,
    @get:Schema(
        description = "Whose the claim's value is: the person's, collected from them and never set by " +
                "a client outside its own audience, or an application's, which a backend answers for " +
                "and the person is never asked to type. Null for a claim this server generates, which " +
                "is nobody's to write.",
        allowableValues = ["personal", "application"],
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val kind: String?,
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
        description = """Grouping identifier (e.g. "profile", "address"), or null if the claim belongs to no group.""",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val group: String?,
    @get:Schema(
        description = "Places this claim is published in: the four that carry its value, and the " +
                "discovery document, which publishes the name alone. Empty when it is published in none, " +
                "which leaves the claim readable through this API and the client API and carried by no " +
                "token.",
        allowableValues = ["id_token", "userinfo", "access_token", "introspection", "discovery"]
    )
    @get:JsonProperty("published_in")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val publishedIn: List<String>,
    @get:Schema(
        description = "OpenID channels this claim is published in only for an authorization whose " +
                "claims request parameter named it. Empty when no channel is open to a request, which " +
                "leaves published_in the whole of where the value goes. A channel never appears in " +
                "both lists.",
        allowableValues = ["id_token", "userinfo"]
    )
    @get:JsonProperty("published_in_when_requested")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val publishedInWhenRequested: List<String>
)
