package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A claim this server knows, as configured."
)
@Serdeable
data class AdminClaimResource(
    @get:Schema(description = "Identifier of the claim.")
    val id: String,
    @get:Schema(
        description = "Data type of the claim's value.",
        allowableValues = ["boolean", "date", "email", "number", "phone_number", "string", "timezone"]
    )
    val type: String,
    @get:Schema(
        description = "Where the claim is defined: by OpenID Connect for `openid`, by the configuration for " +
                "`custom`.",
        allowableValues = ["openid", "custom"]
    )
    val origin: String,
    @get:Schema(
        description = "Whose value the claim holds: the end-user's for `personal`, or a client's for " +
                "`application`, which the end-user is never asked for. Null when this server computes the " +
                "value.",
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
    @get:Schema(
        description = "Whether the end-user must provide a value for this claim before a flow completes."
    )
    val required: Boolean,
    @get:Schema(
        description = "Whether `auth.identifier-claims` names this claim, so that its value identifies the " +
                "end-user when they sign in with a password and when users are merged across providers."
    )
    val identifier: Boolean,
    @get:Schema(
        description = "Values the claim accepts. Null when it accepts any value.",
        nullable = true
    )
    @get:JsonProperty("allowed_values")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val allowedValues: List<Any>?,
    @get:Schema(
        description = "Group the claim is shown in when a flow collects it. Null when it belongs to none.",
        allowableValues = ["identity", "address"],
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val group: String?,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Where this claim's value is sent to clients, whether or not they ask for it. `discovery` " +
                "lists the claim's name in the discovery document, without any value. An enabled claim with an empty " +
                "list is sent nowhere, but its value can still be read through the admin and client APIs. Always " +
                "empty for a disabled claim."
        ),
        schema = Schema(allowableValues = ["id_token", "userinfo", "access_token", "introspection", "discovery"])
    )
    @get:JsonProperty("published_in")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val publishedIn: List<String>,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Where this claim's value is sent only when the client asks for it by name, with the " +
                "`claims` parameter of an OpenID Connect authorization request. A value never appears both here and " +
                "in `published_in`. Always empty for a disabled claim."
        ),
        schema = Schema(allowableValues = ["id_token", "userinfo"])
    )
    @get:JsonProperty("published_in_when_requested")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val publishedInWhenRequested: List<String>
)
