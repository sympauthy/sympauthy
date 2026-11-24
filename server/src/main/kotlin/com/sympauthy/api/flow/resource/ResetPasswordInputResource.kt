package com.sympauthy.api.flow.resource

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Login of the user whose password the end-user asks to reset."
)
@Serdeable
data class ResetPasswordInputResource(
    @get:Schema(
        description = "Login of the end-user: the value of any one of the claims `auth.identifier-claims` " +
            "names. When absent, the request is refused with `flow.password.reset.missing_login`."
    )
    @get:JsonProperty("login")
    val login: String?
)
