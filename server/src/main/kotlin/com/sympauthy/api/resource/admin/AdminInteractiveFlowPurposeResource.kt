package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A purpose of an interactive flow session, and the label a person reads it under. Every " +
            "purpose this surface publishes carries this shape, wherever it appears."
)
@Serdeable
data class AdminInteractiveFlowPurposeResource(
    @get:Schema(
        description = "The purpose. This is the contract: branch on it, and send it back as the 'purpose' " +
                "filter of the listing.",
        allowableValues = [
            "confirm", "oauth2_authorize", "mfa_enrollment", "mfa_challenge", "reauthentication", "link_provider"
        ]
    )
    @get:JsonProperty("value")
    val value: String,
    @get:Schema(
        description = "What the purpose puts the end-user through, phrased for a person. A label rather " +
                "than a key: it may be reworded in any release, so nothing may branch on it."
    )
    @get:JsonProperty("display_name")
    val displayName: String
)
