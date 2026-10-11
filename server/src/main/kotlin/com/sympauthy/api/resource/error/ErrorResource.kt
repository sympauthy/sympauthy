package com.sympauthy.api.resource.error

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Body of a failure, on every surface but the protocol errors of `/api/oauth2`. A caller " +
        "branches on `error_code`."
)
@Serdeable
data class ErrorResource(
    @get:Schema(description = "HTTP status of the response, repeated in the body.")
    val status: Int,

    @get:Schema(description = "Code identifying the failure. It is the contract a caller branches on.")
    @get:JsonProperty("error_code")
    val errorCode: String,

    @get:Schema(
        description = "Message explaining the failure, written to be shown to the end-user in the locale of " +
            "the request. It may be reworded in any release. Absent when no message is written for the failure."
    )
    val description: String?,

    @get:Schema(
        description = "Technical message explaining the failure, for troubleshooting. Absent unless " +
            "`features.print-details-in-error` is enabled."
    )
    val details: String?,

    @get:Schema(
        description = "Refused properties of the request, one entry each. Absent when the failure refuses no " +
            "property in particular."
    )
    val properties: List<PropertyErrorResource>?
)

@Schema(
    description = "Refusal of one property of the request. It carries what the failure holding it carries, " +
        "except the status, which belongs to the response."
)
@Serdeable
data class PropertyErrorResource(
    @get:Schema(description = "Path to the refused property in the request.")
    val path: String,

    @get:Schema(description = "Code identifying why the property was refused.")
    @get:JsonProperty("error_code")
    val errorCode: String,

    @get:Schema(
        description = "Message telling the end-user how to correct the value, in the locale of the request. " +
            "Absent when the refusal names none."
    )
    val description: String?,

    @get:Schema(
        description = "Technical message explaining the refusal, for troubleshooting. Absent unless " +
            "`features.print-details-in-error` is enabled."
    )
    val details: String?
)
