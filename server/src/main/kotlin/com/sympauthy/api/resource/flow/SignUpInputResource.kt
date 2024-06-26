package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonAnySetter
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class SignUpInputResource(
    val password: String
) {
    @set:JsonAnySetter
    var claims: Map<String, Any> = emptyMap()
}
