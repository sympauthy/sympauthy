package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Interactive flow session started to link a provider, and where to send the end-user.")
@Serdeable
data class ClientProviderLinkResource(
    @get:Schema(description = "State of the interactive flow session, which `redirect_url` already carries.")
    val state: String,
    @get:Schema(description = "URL of the first step of the flow, to send the end-user's browser to.")
    @get:JsonProperty("redirect_url")
    val redirectUrl: String
)
