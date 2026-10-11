package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "User with an active consent for this client's audience."
)
@Serdeable
data class ClientUserResource(
    @get:Schema(description = "Identifier of the user.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(
        description = "Values of the claims the end-user signs in with, such as `email`, keyed by claim identifier."
    )
    @get:JsonProperty("identifier_claims")
    val identifierClaims: Map<String, Any?>,
    @get:Schema(description = "Providers linked to the user.")
    val providers: List<ClientProviderResource>,
    @get:Schema(description = "Scopes the end-user consented to for this client's audience.")
    @get:JsonProperty("consented_scopes")
    val consentedScopes: List<String>,
    @get:Schema(description = "Date and time at which the end-user gave their current consent, in UTC.")
    @get:JsonProperty("consented_at")
    val consentedAt: LocalDateTime
)
