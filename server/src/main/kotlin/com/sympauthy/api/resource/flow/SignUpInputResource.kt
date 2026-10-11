package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonAnySetter
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Password of the user to create and, beside it, its identifier claim values keyed by claim " +
        "identifier. A key naming no claim is ignored."
)
@Serdeable
class SignUpInputResource(
    @get:Schema(description = "Password of the user to create.")
    val password: String
) {
    @set:JsonAnySetter
    var claims: Map<String, Any> = emptyMap()
}
