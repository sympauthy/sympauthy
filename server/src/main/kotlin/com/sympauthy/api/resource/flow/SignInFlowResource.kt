package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Everything the custom UI needs to render the sign-in step of the authorization flow.

This resource returns either:
- the configuration of the sign-in step (```password```, ```providers```, ```sign_up_redirect_url```).
- a ```redirect_url``` where the end-user must be redirected to continue the flow. When set, all other fields are null.

Every URL contained in this resource already includes the ```state``` query param.
"""
)
@Serdeable
data class SignInFlowResource(
    @get:Schema(
        description = "Configuration of the password authentication. Null if sign-in by password is disabled."
    )
    val password: PasswordResource? = null,
    @get:Schema(
        description =
            "List of third-party providers the end-user can use to sign in. Null or empty if none is configured."
    )
    val providers: List<ProviderResource>? = null,
    @get:Schema(
        name = "sign_up_redirect_url",
        description = """
URL of the sign-up page the end-user can navigate to to create a new account.
Null when sign-up is not allowed for this flow.
        """
    )
    @get:JsonProperty("sign_up_redirect_url")
    val signUpRedirectUrl: String? = null,
    @get:Schema(
        name = "redirect_url",
        description = """
URL where the end-user must be redirected to continue the authentication flow instead of rendering the sign-in step.
When set, all other fields are null.
        """
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
