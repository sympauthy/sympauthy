package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Response from starting a provider link: where to drive the end-user next."
)
@Serdeable
data class ClientProviderLinkResource(
    @get:Schema(
        description = "Signed state identifying the link session, echoed by the flow endpoints."
    )
    val state: String,
    @get:Schema(
        description = "URL to navigate the end-user to in order to link the provider (state included)."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String
)
