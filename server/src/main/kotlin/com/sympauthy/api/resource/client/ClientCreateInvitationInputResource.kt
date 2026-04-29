package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request body to create an invitation. Audience is derived from the client.")
@Serdeable
data class ClientCreateInvitationInputResource(
    @get:Schema(description = "Expiration date and time. Defaults to now + default-expiration. Capped at now + max-expiration.", nullable = true)
    @get:JsonProperty("expires_at")
    val expiresAt: String?,
    @get:Schema(description = "Custom claims to pre-assign to the user upon registration.", nullable = true)
    val claims: Map<String, String>?,
    @get:Schema(description = "Note.", nullable = true)
    val note: String?,
)
