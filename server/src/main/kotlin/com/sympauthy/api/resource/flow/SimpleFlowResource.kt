package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Response containing the url where the end-user must be redirected to continue the authentication flow.
    """
)
@Serdeable
data class SimpleFlowResource(
    @get:Schema(
        name = "redirect_url",
        description = """
URL where the end-user must be redirected to continue the authentication flow.

The end-user will either:
- continue the authentication flow. ex. if the end-user email address is not validated.
- be redirected to the client if the authentication flow is completed.
        """
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String
)
