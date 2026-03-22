package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "Information about a user who has granted scopes to the client."
)
@Serdeable
data class ClientUserResource(
    @get:Schema(description = "Unique identifier of the user.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(description = "Identifier claims for the user (e.g. email).")
    @get:JsonProperty("identifier_claims")
    val identifierClaims: Map<String, Any?>,
    @get:Schema(description = "Providers linked to the user.")
    val providers: List<ClientProviderResource>,
    @get:Schema(description = "Consentable scopes the user has consented to share with this client.")
    @get:JsonProperty("consented_scopes")
    val consentedScopes: List<String>,
    @get:Schema(description = "The date and time (in UTC timezone) at which the scopes were consented.")
    @get:JsonProperty("consented_at")
    val consentedAt: LocalDateTime
)
