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
    description = "A collectable claim with its metadata and collected/suggested values."
)
@Serdeable
data class ClaimValueResource(
    @get:Schema(
        description = "Identifier of the claim."
    )
    val claim: String,
    @get:Schema(
        description = "Whether this claim is required."
    )
    val required: Boolean,
    @get:Schema(
        description = "Localized display name for this claim."
    )
    val name: String,
    @get:Schema(
        description = "Data type of the claim (e.g. string, email, date, phone_number, timezone)."
    )
    val type: String,
    @get:Schema(
        description = "Group this claim belongs to (e.g. identity, address), or null if ungrouped."
    )
    val group: String?,
    @get:Schema(
        description = """
Whether the end-user has already been presented with this claim during a previous step of the authorization flow.

False if the end-user has never been asked about this claim.
True if the end-user has been presented with this claim, regardless of whether they provided a value or not.
        """
    )
    val collected: Boolean,
    @get:Schema(
        description = """
The value provided by the end-user for this claim, or null if no value was provided.

When collected is false, this is always null (the end-user has not been asked yet).
When collected is true and this is null, it means the end-user was presented with this claim
but chose not to provide a value (e.g. by leaving it empty during the authorization flow).
        """
    )
    val value: Any?,
    @get:Schema(
        description = """
A value for this claim collected from an external provider, suggested to the end-user as a default.
The end-user is free to accept or override this value.
        """
    )
    @get:JsonProperty("suggested_value")
    val suggestedValue: Any? = null
)
