package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Validation code the claims validation step expects the end-user to enter, or the " +
        "`redirect_url` to send the browser to instead. " + STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class ClaimsValidationFlowResource(

    @get:Schema(
        name = "media",
        description = "Media the request asked about.",
        allowableValues = ["email", "sms"]
    )
    @get:JsonProperty("media")
    val media: String,

    @get:Schema(
        name = "code",
        description = "Validation code sent to the end-user through `media`. Absent when `redirect_url` is " +
            "present.",
    )
    @get:JsonProperty("code")
    val code: ValidationCodeResource? = null,

    @get:Schema(
        name = "redirect_url",
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when a validation code is expected through " +
            "`media`."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
