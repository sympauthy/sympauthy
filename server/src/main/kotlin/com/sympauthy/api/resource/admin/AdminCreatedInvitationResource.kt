package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(description = "Newly created invitation with the raw token. The token is only returned at creation time.")
@Serdeable
data class AdminCreatedInvitationResource(
    @get:Schema(description = "Unique identifier of the invitation.")
    @get:JsonProperty("invitation_id")
    val invitationId: UUID,
    @get:Schema(description = "The raw invitation token. Only returned at creation time — store it now.")
    val token: String,
    @get:Schema(description = "Identifier of the audience the invitation is bound to.")
    val audience: String,
    @get:Schema(description = "Current status of the invitation.")
    val status: String,
    @get:Schema(description = "Custom claims pre-assigned to the user upon registration.", nullable = true)
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val claims: Map<String, String>?,
    @get:Schema(description = "Admin note.", nullable = true)
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val note: String?,
    @get:Schema(description = "Date and time the invitation was created.")
    @get:JsonProperty("created_at")
    val createdAt: LocalDateTime,
    @get:Schema(description = "Date and time the invitation expires.")
    @get:JsonProperty("expires_at")
    val expiresAt: LocalDateTime,
)
