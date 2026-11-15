package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_CANCEL_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_CLIENT_DESCRIPTION
import com.sympauthy.api.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Configuration of the step where the end-user asks to reset a forgotten password, or the " +
        "`redirect_url` to send the browser to instead. " + STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class ResetPasswordFlowResource(
    @get:Schema(
        name = "identifier_claims",
        description = "Identifier claims: the value of any one of them is the login whose password is reset. " +
            "Absent when `redirect_url` is present."
    )
    @get:JsonProperty("identifier_claims")
    val identifierClaims: List<CollectableClaimResource>? = null,
    @get:Schema(
        description = STEP_CLIENT_DESCRIPTION + " Absent too when `redirect_url` is present."
    )
    @get:JsonProperty("client")
    val client: ClientReferenceResource? = null,
    @get:Schema(
        name = "cancel_url",
        description = STEP_CANCEL_URL_DESCRIPTION + " Absent too when `redirect_url` is present."
    )
    @get:JsonProperty("cancel_url")
    val cancelUrl: String? = null,
    @get:Schema(
        name = "redirect_url",
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when the end-user is expected to ask for a code on " +
            "this step."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
