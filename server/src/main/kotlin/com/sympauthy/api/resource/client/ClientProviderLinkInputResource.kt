package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request to start linking a provider for an end-user signed in to this client.")
@Serdeable
data class ClientProviderLinkInputResource(
    @get:Schema(
        description = "Access token this server issued to this client for the end-user, still active. The " +
            "provider is linked to the user it was issued for. When absent, the request is refused."
    )
    @get:JsonProperty("access_token")
    val accessToken: String?,
    @get:Schema(
        description = "URL the end-user's browser is sent to once the link ends, whether it completed or not. " +
            "It must be this client's `clients.<id>.return-uri` or one of its `clients.<id>.allowed-return-uris`. " +
            "When absent, `clients.<id>.return-uri`, and the request is refused where the configuration gives " +
            "none."
    )
    @get:JsonProperty("return_uri")
    val returnUri: String? = null,
    @get:Schema(
        description = "Value this server sends back unchanged as `sympauthy_return_state` when it sends the " +
            "end-user's browser to the return URL, so the client can tell that return from one it did not ask " +
            "for. At most 512 characters. " +
            "When absent, the browser is sent back without one."
    )
    @get:JsonProperty("return_state")
    val returnState: String? = null
)
