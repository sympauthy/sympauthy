package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Configuration of the sign-up step, or the `redirect_url` to send the browser to instead. " +
        STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class SignUpFlowResource(
    @get:Schema(
        description = "Configuration of signing up with a password. Absent when signing up with a password is " +
            "disabled, or when `redirect_url` is present."
    )
    val password: PasswordResource? = null,
    @get:Schema(
        name = "sign_in_redirect_url",
        description = "URL of the sign-in step, for an end-user who already has a user. Absent when an " +
            "invitation started the flow, or when `redirect_url` is present."
    )
    @get:JsonProperty("sign_in_redirect_url")
    val signInRedirectUrl: String? = null,
    @get:Schema(
        name = "redirect_url",
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when the end-user is expected to sign up on this " +
            "step."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
