package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request to create an invitation.")
@Serdeable
data class AdminCreateInvitationInputResource(
    @get:Schema(description = "Identifier of the audience whose clients accept the invitation.", required = true)
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(
        description = "Date and time at which the invitation expires, in UTC and with no zone. One later " +
                "than `advanced.invitation.max-expiration` from now is brought back to it. When absent, " +
                "`advanced.invitation.default-expiration` from now.",
        nullable = true
    )
    @get:JsonProperty("expires_at")
    val expiresAt: String?,
    @get:Schema(
        description = "Claim values to apply to the user who consumes the invitation, keyed by claim " +
                "identifier. Each must be an enabled claim not restricted to another audience. When absent, " +
                "the invitation applies none.",
        nullable = true
    )
    val claims: Map<String, String>?,
    @get:Schema(
        description = "Note to leave on the invitation for whoever manages it, never shown to the " +
                "end-user. When absent, it carries none.",
        nullable = true
    )
    val note: String?,
)
