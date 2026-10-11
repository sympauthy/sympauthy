package com.sympauthy.api.resource.error

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Body of a protocol error on `/api/oauth2`.")
@Serdeable
data class OAuth2ErrorResource(
    @get:Schema(description = "Error code the specification defines, such as `invalid_grant`. A client branches on it.")
    @get:JsonProperty("error_code")
    val errorCode: String,

    @get:Schema(
        description = "Technical message explaining the error, for troubleshooting. Absent unless " +
            "`features.print-details-in-error` is enabled."
    )
    val details: String? = null,

    @get:Schema(
        description = "Message explaining the error, written to be shown to the end-user in the locale of the " +
            "request. It may be reworded in any release. Absent when no message is written for the error."
    )
    val description: String? = null,
)
