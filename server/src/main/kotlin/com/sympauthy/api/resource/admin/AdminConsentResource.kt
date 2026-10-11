package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "A consent: the scopes an end-user granted an audience."
)
@Serdeable
data class AdminConsentResource(
    @get:Schema(description = "Identifier of the consent.")
    @get:JsonProperty("consent_id")
    val consentId: UUID,
    @get:Schema(description = "Identifier of the user the consent belongs to.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(description = "Identifier of the audience the consent applies to.")
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(description = "Identifier of the client whose authorization request last updated the consent.")
    @get:JsonProperty("prompted_by_client_id")
    val promptedByClientId: String,
    @get:Schema(description = "Scopes the end-user consented to for the audience.")
    val scopes: List<String>,
    @get:Schema(description = "Date and time at which the end-user consented, in UTC.")
    @get:JsonProperty("consented_at")
    val consentedAt: LocalDateTime,
    @get:Schema(
        description = "Date and time at which the consent was revoked, in UTC. Null when the consent is " +
                "active.",
        nullable = true
    )
    @get:JsonProperty("revoked_at")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val revokedAt: LocalDateTime?,
    @get:Schema(
        description = "Who revoked the consent. Null when the consent is active.",
        nullable = true,
        allowableValues = ["user", "admin"]
    )
    @get:JsonProperty("revoked_by")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val revokedBy: String?,
    @get:Schema(
        description = "Identifier of the user who revoked the consent. Null when the consent is active.",
        nullable = true
    )
    @get:JsonProperty("revoked_by_id")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val revokedById: UUID?
)
