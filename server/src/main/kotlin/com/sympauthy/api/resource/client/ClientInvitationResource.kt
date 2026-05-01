package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(description = "Information about an invitation created by this client.")
@Serdeable
data class ClientInvitationResource(
    @get:Schema(description = "Unique identifier of the invitation.")
    @get:JsonProperty("invitation_id")
    val invitationId: UUID,
    @get:Schema(description = "First 8 characters of the token, for identification.")
    @get:JsonProperty("token_prefix")
    val tokenPrefix: String,
    @get:Schema(description = "Current status of the invitation.", allowableValues = ["pending", "consumed", "revoked", "expired"])
    val status: String,
    @get:Schema(description = "Custom claims pre-assigned to the user upon registration.", nullable = true)
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val claims: Map<String, String>?,
    @get:Schema(description = "Note.", nullable = true)
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val note: String?,
    @get:Schema(description = "Date and time the invitation was created.")
    @get:JsonProperty("created_at")
    val createdAt: LocalDateTime,
    @get:Schema(description = "Date and time the invitation expires.")
    @get:JsonProperty("expires_at")
    val expiresAt: LocalDateTime,
    @get:Schema(description = "Identifier of the user who used this invitation.", nullable = true)
    @get:JsonProperty("user_id")
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val userId: UUID?,
    @get:Schema(description = "Date and time the invitation was used.", nullable = true)
    @get:JsonProperty("consumed_at")
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val consumedAt: LocalDateTime?,
)
