package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
class FlowErrorResource(
    @get:Schema(
        description = """
A code identifying the error that caused the authentication flow to fail.

If the authentication flow is still ongoing, the redirect_url property will contain the URL where the end-user must 
be redirected to continue the authentication flow.
        """
    )
    @get:JsonProperty("error_code")
    val errorCode: String? = null,

    @get:Schema(description = "A message explaining the error to the end-user. It may contain information on how to recover from the issue.")
    val description: String? = null,

    @get:Schema(description = "A message containing technical details about the error.")
    val details: String? = null,

    @get:Schema(
        name = "redirect_url",
        description = """
URL where the end-user must be redirected to continue the authentication flow if there is no error.

The end-user will either:
- continue the authentication flow.
- be redirected to the client if the authentication flow is completed.
"""
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
