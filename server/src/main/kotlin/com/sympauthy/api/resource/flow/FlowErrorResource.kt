package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Failure the error page shows, or the `redirect_url` to send the browser to instead. " +
        STEP_URL_STATE_DESCRIPTION
)
@Serdeable
class FlowErrorResource(
    @get:Schema(
        description = "Error code the interactive flow session failed with, or the one the `state` was refused " +
            "with. Absent when `redirect_url` is present."
    )
    @get:JsonProperty("error_code")
    val errorCode: String? = null,

    @get:Schema(
        description = "Message explaining the failure to the end-user, in the language the request asks for. " +
            "Absent when `redirect_url` is present."
    )
    val description: String? = null,

    @get:Schema(
        description = "Technical message about the failure, for troubleshooting. Absent unless " +
            "`features.print-details-in-error` is enabled, and absent when `redirect_url` is present."
    )
    val details: String? = null,

    @get:Schema(
        name = "redirect_url",
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when the interactive flow session has failed, or " +
            "when the `state` resolves to none."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
