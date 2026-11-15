package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "New password the end-user chose."
)
@Serdeable
data class ResetPasswordChoiceInputResource(
    @get:Schema(
        description = "New password of the end-user. It replaces every other password of their user once the " +
            "interactive flow session completes. When absent, the request is refused with " +
            "`flow.password.reset.missing_password`."
    )
    @get:JsonProperty("password")
    val password: String?
)
