package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
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
    @get:Schema(description = "The collected value of the claim, or null if not collected.", nullable = true)
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val value: Any?,
    @get:Schema(description = "Data type of the claim (e.g. string, boolean).")
    val type: String,
    @get:Schema(description = "Where the claim is defined.", allowableValues = ["openid", "custom"])
    val origin: String,
    @get:Schema(description = "Whether this claim is required.")
    val required: Boolean,
    @get:Schema(description = "Whether this claim is used as an identifier.")
    val identifier: Boolean,
    @get:Schema(description = "Grouping identifier (e.g. \"profile\", \"address\"), or null if the claim belongs to no group.", nullable = true)
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val group: String?,
    @get:Schema(description = "Date and time (UTC) at which the claim value was collected, or null.", nullable = true)
    @get:JsonProperty("collected_at")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val collectedAt: LocalDateTime?,
    @get:Schema(description = "Date and time (UTC) at which the claim value was verified, or null.", nullable = true)
    @get:JsonProperty("verified_at")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val verifiedAt: LocalDateTime?
)
