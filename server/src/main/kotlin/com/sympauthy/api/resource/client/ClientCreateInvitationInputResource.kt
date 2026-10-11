package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Invitation to create. Its audience is this client's.")
@Serdeable
data class ClientCreateInvitationInputResource(
    @get:Schema(
        description = "Date and time at which the invitation expires, in UTC and with no zone. One later than " +
            "`advanced.invitation.max-expiration` from now is brought back to it. When absent, " +
            "`advanced.invitation.default-expiration` from now.",
        nullable = true
    )
    @get:JsonProperty("expires_at")
    val expiresAt: String?,
    @get:Schema(
        description = "Claim values to apply to the user who consumes the invitation, keyed by claim " +
            "identifier. Each must be a claim of this client's audience that this client may write. When " +
            "absent, the invitation applies none.",
        nullable = true
    )
    val claims: Map<String, String>?,
    @get:Schema(description = "Note to leave on the invitation. When absent, it carries none.", nullable = true)
    val note: String?,
)
