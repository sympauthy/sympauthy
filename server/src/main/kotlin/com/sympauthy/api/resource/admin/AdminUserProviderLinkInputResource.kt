package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Request to start linking a provider to a user."
)
@Serdeable
data class AdminUserProviderLinkInputResource(
    @get:Schema(
        description = "Identifier of the client the end-user is sent back to. `return_uri` and `cancel_uri` " +
                "must be among its `clients.<id>.allowed-redirect-uris`. When absent, the request is refused."
    )
    @get:JsonProperty("client_id")
    val clientId: String?,
    @get:Schema(
        description = "URL the end-user's browser is sent to once the provider is linked. When absent, the " +
                "request is refused."
    )
    @get:JsonProperty("return_uri")
    val returnUri: String?,
    @get:Schema(
        description = "URL the end-user's browser is sent to if the end-user cancels the link. When absent, " +
                "the link cannot be cancelled."
    )
    @get:JsonProperty("cancel_uri")
    val cancelUri: String? = null
)
