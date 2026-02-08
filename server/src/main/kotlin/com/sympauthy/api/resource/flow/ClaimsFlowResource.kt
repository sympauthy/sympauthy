package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Resource containing either:
- claims collected by the authorization server for the end-user signing-in/up in the authorization flow.
- a redirect url where the end-user must be redirected to continue the authentication flow.
"""
)
@Serdeable
data class ClaimsFlowResource(
    @get:Schema(
        description = "List of claims."
    )
    val claims: List<ClaimValueResource>? = null,

    @get:Schema(
        name = "redirect_url",
        description = """
URL where the end-user must be redirected to continue the authentication flow.

The end-user will either:
- continue the authentication flow. ex. if there is no claims to collect from the end-user.
- be redirected to the client if the authentication flow is completed.
        """
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)

@Schema(
    description = "A claim and the value the authorization server has collected for it."
)
@Serdeable
data class ClaimValueResource(
    @get:Schema(
        description = "The claim."
    )
    val claim: String,
    @get:Schema(
        description = """
True if a value for this claim has been collected by the authorization server as a first-party.
        """
    )
    val collected: Boolean,
    @get:Schema(
        description = """
A value for the claim that the authorization server has collected as a first-party (through API or authorization flow).

If this value is missing and collected is true, it means the authorization server has already asked the end-user 
about this claims but the end-user declined to fill the claim (ex. by leaving it empty during the authentication flow).
        """
    )
    val value: Any?,
    @get:Schema(
        description = """
A value for the claim that the authorization server has collected from an external provider.  
        """
    )
    @get:JsonProperty("suggested_value")
    val suggestedValue: Any? = null
)
