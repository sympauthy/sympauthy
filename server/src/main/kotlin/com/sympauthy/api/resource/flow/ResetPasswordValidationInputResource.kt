package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Code the end-user entered to reset their password."
)
@Serdeable
data class ResetPasswordValidationInputResource(
    @get:Schema(
        description = "Code the end-user received. When absent, the code is refused with " +
            "`flow.password.reset.invalid_code`."
    )
    @get:JsonProperty("code")
    val code: String?
)
