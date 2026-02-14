package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Resource indicating whether the end-user must validate its claims through a code. If a code is present, 
the user is expected to enter a code received through the media to validate its claims. 

Otherwise the user must be redirected to the redirect URL to continue the authorization flow.
"""
)
@Serdeable
data class ClaimsValidationFlowResource(

    @get:Schema(
        name = "media",
        description = "The media requested."
    )
    @get:JsonProperty("media")
    val media: String,

    @get:Schema(
        name = "code",
        description = "Information about the validation code sent to the user by the authorization server.",
    )
    @get:JsonProperty("code")
    val code: ValidationCodeResource? = null,

    @get:Schema(
        name = "redirect_url",
        description = """
URL where the end-user must be redirected to continue the authentication flow.
The URL is present only if no validation is required from the user through the media.

The end-user will either:
- continue the authentication flow.
- be redirected to the client if the authentication flow is completed.
        """
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
