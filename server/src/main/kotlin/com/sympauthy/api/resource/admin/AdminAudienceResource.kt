package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Information about a configured audience."
)
@Serdeable
data class AdminAudienceResource(
    @get:Schema(
        description = "Unique identifier of the audience, as defined in the YAML configuration key."
    )
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(
        description = "Value used as the aud claim in access and refresh tokens issued for clients belonging to this audience. Defaults to the audience identifier when not explicitly configured."
    )
    @get:JsonProperty("token_audience")
    val tokenAudience: String,
    @get:Schema(
        description = "Whether open registration is enabled for this audience. When true, any user can create an account through the sign-up flow."
    )
    @get:JsonProperty("sign_up_enabled")
    val signUpEnabled: Boolean,
    @get:Schema(
        description = "Whether invitation-based registration is enabled for this audience. When true, invitations can be created and used to register."
    )
    @get:JsonProperty("invitation_enabled")
    val invitationEnabled: Boolean,
    @get:Schema(
        description = "Number of clients configured for this audience."
    )
    @get:JsonProperty("clients_count")
    val clientsCount: Int
)
