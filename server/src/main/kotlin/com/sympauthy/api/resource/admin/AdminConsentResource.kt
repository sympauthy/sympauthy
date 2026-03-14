package com.sympauthy.api.resource.admin

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
    @get:Schema(description = "Identifier of the client the user consented to.")
    @get:JsonProperty("client_id")
    val clientId: String,
    @get:Schema(description = "List of scope identifiers the user consented to for this client.")
    val scopes: List<String>,
    @get:Schema(description = "Date and time at which the user consented.")
    @get:JsonProperty("consented_at")
    val consentedAt: LocalDateTime,
    @get:Schema(description = "Date and time at which the consent was revoked, or null if still active.")
    @get:JsonProperty("revoked_at")
    val revokedAt: LocalDateTime?,
    @get:Schema(description = "Actor who revoked the consent, or null if still active. " +
        "Values: user (revoked by the user themselves), admin (revoked by an administrator).")
    @get:JsonProperty("revoked_by")
    val revokedBy: String?,
    @get:Schema(description = "Identifier of the user or admin who revoked the consent, or null if still active.")
    @get:JsonProperty("revoked_by_id")
    val revokedById: UUID?
)
