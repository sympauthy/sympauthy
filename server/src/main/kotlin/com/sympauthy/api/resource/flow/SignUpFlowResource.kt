package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Everything the custom UI needs to render the sign-up step of the authorization flow.

This resource returns either:
- the configuration of the sign-up step (```password```, ```open_registration```, ```invitation```, ```claims```, ```sign_in_redirect_url```).
- a ```redirect_url``` where the end-user must be redirected to continue the flow. When set, all other fields are null.

Every URL contained in this resource already includes the ```state``` query param.
"""
)
@Serdeable
data class SignUpFlowResource(
    @get:Schema(
        description = "Configuration of the password authentication. Null if sign-up by password is disabled."
    )
    val password: PasswordResource? = null,
    @get:Schema(
        description = "List of identifier claims collected by the authorization server during sign-up."
    )
    val claims: List<CollectableClaimResource>? = null,
    @get:Schema(
        name = "sign_in_redirect_url",
        description = """
URL of the sign-in page the end-user can navigate to if they already have an account.
Null when sign-in is not allowed for this flow (ex. during an invitation flow).
        """
    )
    @get:JsonProperty("sign_in_redirect_url")
    val signInRedirectUrl: String? = null,
    @get:Schema(
        name = "redirect_url",
        description = """
URL where the end-user must be redirected to continue the authentication flow instead of rendering the sign-up step.
When set, all other fields are null.
        """
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
