package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "Information about a claim collected from a user."
)
@Serdeable
data class AdminUserClaimResource(
    @get:Schema(description = "Identifier of the claim.")
    @get:JsonProperty("claim_id")
    val claimId: String,
    @get:Schema(description = "The collected value of the claim, or null if not collected.")
    val value: Any?,
    @get:Schema(description = "Data type of the claim (e.g. string, boolean).")
    val type: String,
    @get:Schema(description = "Whether this is a standard OpenID Connect claim.")
    val standard: Boolean,
    @get:Schema(description = "Whether this claim is required.")
    val required: Boolean,
    @get:Schema(description = "Whether this claim is used as an identifier.")
    val identifier: Boolean,
    @get:Schema(description = "Group this claim belongs to, if any.")
    val group: String?,
    @get:Schema(description = "Date and time at which the claim value was collected.")
    @get:JsonProperty("collected_at")
    val collectedAt: LocalDateTime?,
    @get:Schema(description = "Date and time at which the claim value was verified.")
    @get:JsonProperty("verified_at")
    val verifiedAt: LocalDateTime?
)
