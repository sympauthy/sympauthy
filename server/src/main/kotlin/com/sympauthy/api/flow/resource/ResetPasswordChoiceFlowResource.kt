package com.sympauthy.api.flow.resource

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.flow.util.STEP_CANCEL_URL_DESCRIPTION
import com.sympauthy.api.flow.util.STEP_CLIENT_DESCRIPTION
import com.sympauthy.api.flow.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.flow.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Configuration of the step where the end-user chooses the new password of their user, or the " +
        "`redirect_url` to send the browser to instead. " + STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class ResetPasswordChoiceFlowResource(
    @get:Schema(
        description = "Login the end-user asked to reset the password of, as they entered it, for the page to " +
            "offer to a password manager beside the new password. Absent when `redirect_url` is present."
    )
    @get:JsonProperty("login")
    val login: String? = null,
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
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when the end-user is expected to choose a password " +
            "on this step."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
