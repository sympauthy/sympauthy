package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Configuration of the sign-in step, or the `redirect_url` to send the browser to instead. " +
        STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class SignInFlowResource(
    @get:Schema(
        description = "Configuration of signing in with a password. Absent when signing in with a password is " +
            "disabled, when the end-user is re-authenticating and their user has no password, or when " +
            "`redirect_url` is present."
    )
    val password: PasswordResource? = null,
    @get:Schema(
        description = "Providers the end-user can sign in with; when re-authenticating, only those linked to " +
            "their user. Absent when there is none, or when `redirect_url` is present."
    )
    val providers: List<ProviderResource>? = null,
    @get:Schema(
        name = "sign_up_redirect_url",
        description = "URL of the sign-up step, for an end-user who has no user yet. Absent when the client's " +
            "audience does not allow signing up, when the flow configures no sign-up step, when the end-user " +
            "is re-authenticating, or when `redirect_url` is present."
    )
    @get:JsonProperty("sign_up_redirect_url")
    val signUpRedirectUrl: String? = null,
    @get:Schema(
        name = "redirect_url",
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when the end-user is expected to sign in on this " +
            "step."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
