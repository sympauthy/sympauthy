package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_CANCEL_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_CLIENT_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Step asking the end-user for the TOTP code their authenticator shows."
)
@Serdeable
data class TotpChallengeFlowResource(
    @get:Schema(description = STEP_CLIENT_DESCRIPTION)
    @get:JsonProperty("client")
    val client: ClientReferenceResource? = null,

    @get:Schema(name = "cancel_url", description = STEP_CANCEL_URL_DESCRIPTION)
    @get:JsonProperty("cancel_url")
    val cancelUrl: String? = null
)
