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
    @get:Schema(
        description = "Whether this claim is in use. A disabled claim is listed here and nowhere else: it " +
                "is not collected, not written, and not sent to clients. Values already stored for it are " +
                "kept, and are sent again if the claim is enabled again."
    )
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
        description = "Where this claim's value is sent to clients, whether or not they ask for it: " +
                "id_token, userinfo, access_token, introspection. discovery lists the claim's name in the " +
                "discovery document, without any value. An enabled claim with an empty list is sent " +
                "nowhere, but its value can still be read through the admin and client APIs. Always empty " +
                "for a disabled claim.",
        allowableValues = ["id_token", "userinfo", "access_token", "introspection", "discovery"]
    )
    @get:JsonProperty("published_in")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val publishedIn: List<String>,
    @get:Schema(
        description = "Where this claim's value is sent only when the client asks for it by name, with " +
                "the claims parameter of an OpenID Connect authorization request. A place never appears " +
                "both here and in published_in. Always empty for a disabled claim.",
        allowableValues = ["id_token", "userinfo"]
    )
    @get:JsonProperty("published_in_when_requested")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val publishedInWhenRequested: List<String>
)
