package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Request body to start linking an identity provider to a signed-in end-user's account."
)
@Serdeable
data class ClientProviderLinkInputResource(
    @get:Schema(
        description = """
Access token identifying the end-user whose account the provider will be linked to.
Must be a valid, unexpired user access token issued by this authorization server to the calling client.
        """
    )
    @get:JsonProperty("access_token")
    val accessToken: String?,
    @get:Schema(
        description = """
URI the end-user is returned to once the provider link completes.
Must be one of the calling client's registered redirect URIs.
        """
    )
    @get:JsonProperty("return_uri")
    val returnUri: String?,
    @get:Schema(
        description = """
Optional URI the end-user is returned to if they cancel the provider link.
When provided, must be one of the calling client's registered redirect URIs. When omitted, the flow offers
no cancellation.
        """
    )
    @get:JsonProperty("cancel_uri")
    val cancelUri: String? = null
)
