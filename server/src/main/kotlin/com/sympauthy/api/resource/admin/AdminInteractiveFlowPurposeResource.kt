package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Something an interactive flow session takes the end-user through, such as signing in or " +
            "setting up a second factor."
)
@Serdeable
data class AdminInteractiveFlowPurposeResource(
    @get:Schema(
        description = "The purpose, as the `purpose` criterion of the interactive flow session collection " +
                "takes it.",
        allowableValues = [
            "confirm", "oauth2_authorize", "mfa_enrollment", "mfa_challenge", "reauthentication", "link_provider"
        ]
    )
    @get:JsonProperty("value")
    val value: String,
    @get:Schema(
        description = "What the purpose takes the end-user through, as a label in English. It may be reworded " +
                "in any release."
    )
    @get:JsonProperty("display_name")
    val displayName: String
)
