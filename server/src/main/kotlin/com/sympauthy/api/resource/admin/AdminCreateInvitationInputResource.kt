package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request body to create an invitation.")
@Serdeable
data class AdminCreateInvitationInputResource(
    @get:Schema(description = "Identifier of the audience the invitation is bound to.", required = true)
    val audience: String,
    @get:Schema(description = "Expiration date and time. Defaults to now + default-expiration. Capped at now + max-expiration.", nullable = true)
    @get:JsonProperty("expires_at")
    val expiresAt: String?,
    @get:Schema(description = "Custom claims to pre-assign to the user upon registration.", nullable = true)
    val claims: Map<String, String>?,
    @get:Schema(description = "Admin note.", nullable = true)
    val note: String?,
)
