package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.FLOW_REDIRECT_URL_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Where to send the browser next."
)
@Serdeable
data class SimpleFlowResource(
    @get:Schema(
        name = "redirect_url",
        description = FLOW_REDIRECT_URL_DESCRIPTION
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String
)
