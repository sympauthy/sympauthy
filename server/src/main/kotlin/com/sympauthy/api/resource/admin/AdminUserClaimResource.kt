package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "A claim, with the value a user holds for it."
)
@Serdeable
data class AdminUserClaimResource(
    @get:Schema(description = "Identifier of the claim.")
    @get:JsonProperty("claim_id")
    val claimId: String,
    @get:Schema(
        description = "Value the user holds for the claim. Null when the user holds none.",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val value: Any?,
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
        description = "Whether the end-user must provide a value for this claim before a flow completes."
    )
    val required: Boolean,
    @get:Schema(
        description = "Whether `auth.identifier-claims` names this claim, so that its value identifies the " +
                "end-user when they sign in with a password and when users are merged across providers."
    )
    val identifier: Boolean,
    @get:Schema(
        description = "Group the claim is shown in when a flow collects it. Null when it belongs to none.",
        allowableValues = ["identity", "address"],
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val group: String?,
    @get:Schema(
        description = "Date and time at which the value was collected, in UTC. Null when nothing was " +
                "collected, or when this server computes the value.",
        nullable = true
    )
    @get:JsonProperty("collected_at")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val collectedAt: LocalDateTime?,
    @get:Schema(
        description = "Date and time at which the value was verified, in UTC. Null when it is not verified, " +
                "or when this server computes the value.",
        nullable = true
    )
    @get:JsonProperty("verified_at")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val verifiedAt: LocalDateTime?
)
