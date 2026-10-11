package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonAnySetter
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Claim values the end-user entered, keyed by claim identifier. A key naming no claim is " +
        "ignored."
)
@Serdeable
class ClaimInputResource {
    @set:JsonAnySetter
    var claims: Map<String, Any> = emptyMap()
}
