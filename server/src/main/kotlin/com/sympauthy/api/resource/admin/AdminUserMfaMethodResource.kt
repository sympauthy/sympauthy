package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "Information about a registered MFA method."
)
@Serdeable
data class AdminUserMfaMethodResource(
    @get:Schema(description = "Unique identifier of the MFA registration.")
    @get:JsonProperty("mfa_id")
    val mfaId: UUID,
    @get:Schema(description = "Type of MFA method.", allowableValues = ["totp"])
    val type: String,
    @get:Schema(description = "Date and time at which the MFA method was registered.")
    @get:JsonProperty("registered_at")
    val registeredAt: LocalDateTime
)
