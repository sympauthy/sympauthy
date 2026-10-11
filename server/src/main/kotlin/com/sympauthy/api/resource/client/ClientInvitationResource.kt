package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(description = "Invitation this client created.")
@Serdeable
data class ClientInvitationResource(
    @get:Schema(description = "Identifier of the invitation.")
    @get:JsonProperty("invitation_id")
    val invitationId: UUID,
    @get:Schema(description = "First 8 characters of the invitation's token, to recognize the invitation by.")
    @get:JsonProperty("token_prefix")
    val tokenPrefix: String,
    @get:Schema(
        description = "Status of the invitation. One still pending at `expires_at` is `expired`.",
        allowableValues = ["pending", "consumed", "revoked", "expired"]
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
    @get:Schema(
        description = "Identifier of the user who consumed the invitation. Absent unless it is `consumed`.",
        nullable = true
    )
    @get:JsonProperty("user_id")
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val userId: UUID?,
    @get:Schema(
        description = "Date and time at which the invitation was consumed, in UTC. Absent unless it is `consumed`.",
        nullable = true
    )
    @get:JsonProperty("consumed_at")
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val consumedAt: LocalDateTime?,
)
