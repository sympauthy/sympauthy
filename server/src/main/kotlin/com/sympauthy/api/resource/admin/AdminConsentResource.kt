package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "Information about a consent given by a user to a client."
)
@Serdeable
data class AdminConsentResource(
    @get:Schema(description = "Unique identifier of the consent.")
    @get:JsonProperty("consent_id")
    val consentId: UUID,
    @get:Schema(description = "Identifier of the user who consented.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(description = "Identifier of the audience the consent applies to.")
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(description = "Identifier of the client that originally prompted the consent.")
    @get:JsonProperty("prompted_by_client_id")
    val promptedByClientId: String,
    @get:Schema(description = "List of scope identifiers the user consented to for this audience.")
    val scopes: List<String>,
    @get:Schema(description = "Date and time at which the user consented.")
    @get:JsonProperty("consented_at")
    val consentedAt: LocalDateTime,
    @get:Schema(
        description = "Date and time at which the consent was revoked, or null if still active.",
        nullable = true
    )
    @get:JsonProperty("revoked_at")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val revokedAt: LocalDateTime?,
    @get:Schema(
        description = "Actor who revoked the consent, or null if still active.",
        nullable = true,
        allowableValues = ["user", "admin"]
    )
    @get:JsonProperty("revoked_by")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val revokedBy: String?,
    @get:Schema(
        description = "Identifier of the user or admin who revoked the consent, or null if still active.",
        nullable = true
    )
    @get:JsonProperty("revoked_by_id")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val revokedById: UUID?
)
