package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(description = "Invitation this client just created, with its token.")
@Serdeable
data class ClientCreatedInvitationResource(
    @get:Schema(description = "Identifier of the invitation.")
    @get:JsonProperty("invitation_id")
    val invitationId: UUID,
    @get:Schema(
        description = "Token of the invitation, sent as `invitation_token` on the authorization request of the " +
            "end-user it invites. No other response carries it."
    )
    val token: String,
    @get:Schema(
        description = "Status of the invitation. It is `expired` only where `expires_at` was asked for in the " +
            "past.",
        allowableValues = ["pending", "expired"]
    )
    val status: String,
    @get:Schema(
        description = "Claim values applied to the user who consumes the invitation, keyed by claim identifier. " +
            "Absent when the invitation was created without `claims`.",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val claims: Map<String, String>?,
    @get:Schema(
        description = "Note left on the invitation. Absent when it was created without one.",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val note: String?,
    @get:Schema(description = "Date and time at which the invitation was created, in UTC.")
    @get:JsonProperty("created_at")
    val createdAt: LocalDateTime,
    @get:Schema(description = "Date and time at which the invitation expires, in UTC.")
    @get:JsonProperty("expires_at")
    val expiresAt: LocalDateTime,
)
