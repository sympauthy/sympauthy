package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "A grant, as the collection lists it: what a client was allowed, under which every token is " +
            "issued."
)
@Serdeable
data class AdminGrantSummaryResource(
    @get:Schema(description = GRANT_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("grant_id")
    val grantId: UUID,
    @get:Schema(description = GRANT_USER_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("user_id")
    val userId: UUID?,
    @get:Schema(description = GRANT_CLIENT_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("client_id")
    val clientId: String,
    @get:Schema(description = GRANT_AUDIENCE_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(
        description = GRANT_TYPE_PROPERTY_DESCRIPTION,
        allowableValues = [
            "authorization_code",
            "client_credentials",
            "urn:ietf:params:oauth:grant-type:token-exchange"
        ]
    )
    @get:JsonProperty("grant_type")
    val grantType: String,
    @get:Schema(
        description = GRANT_STATUS_PROPERTY_DESCRIPTION,
        allowableValues = ["live", "revoked", "expired", "lapsed"]
    )
    @get:JsonProperty("status")
    val status: String,
    @get:Schema(description = GRANT_CREATED_AT_PROPERTY_DESCRIPTION)
    @get:JsonProperty("created_at")
    val createdAt: LocalDateTime,
    @get:Schema(description = GRANT_EXPIRES_AT_PROPERTY_DESCRIPTION)
    @get:JsonProperty("expires_at")
    val expiresAt: LocalDateTime?,
    @get:Schema(description = GRANT_REVOKED_AT_PROPERTY_DESCRIPTION)
    @get:JsonProperty("revoked_at")
    val revokedAt: LocalDateTime?,
    @get:Schema(
        description = GRANT_REVOKED_BY_PROPERTY_DESCRIPTION,
        allowableValues = ["client", "admin", "consent_revoked", "authorization_code_replayed"]
    )
    @get:JsonProperty("revoked_by")
    val revokedBy: String?,
    @get:Schema(description = GRANT_ACTOR_GRANT_ID_PROPERTY_DESCRIPTION)
    @get:JsonProperty("actor_grant_id")
    val actorGrantId: UUID?
)
