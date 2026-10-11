package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "An audience the configuration declares."
)
@Serdeable
data class AdminAudienceResource(
    @get:Schema(description = "Identifier of the audience.")
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(
        description = "Value of the `aud` claim in the access and refresh tokens issued to the audience's " +
                "clients. The audience's identifier unless the configuration sets another."
    )
    @get:JsonProperty("token_audience")
    val tokenAudience: String,
    @get:Schema(
        description = "Whether an end-user may sign up through the audience's clients without an invitation."
    )
    @get:JsonProperty("sign_up_enabled")
    val signUpEnabled: Boolean,
    @get:Schema(
        description = "Whether invitations can be created for the audience and used to sign up."
    )
    @get:JsonProperty("invitation_enabled")
    val invitationEnabled: Boolean,
    @get:Schema(description = "Number of clients the audience groups.")
    @get:JsonProperty("clients_count")
    val clientsCount: Int
)
