package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "An MFA method enrolled for a user."
)
@Serdeable
data class AdminUserMfaMethodResource(
    @get:Schema(description = "Identifier of the MFA method.")
    @get:JsonProperty("mfa_id")
    val mfaId: UUID,
    @get:Schema(description = "Type of the MFA method.", allowableValues = ["totp"])
    val type: String,
    @get:Schema(description = "Date and time at which the end-user confirmed the MFA method, in UTC.")
    @get:JsonProperty("registered_at")
    val registeredAt: LocalDateTime
)
