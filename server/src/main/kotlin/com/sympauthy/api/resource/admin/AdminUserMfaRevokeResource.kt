package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(
    description = "Result of an MFA method revocation."
)
@Serdeable
data class AdminUserMfaRevokeResource(
    @get:Schema(description = "Unique identifier of the user.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(description = "Unique identifier of the revoked MFA registration.")
    @get:JsonProperty("mfa_id")
    val mfaId: UUID,
    @get:Schema(description = "Whether the MFA method was successfully revoked.")
    val revoked: Boolean
)
