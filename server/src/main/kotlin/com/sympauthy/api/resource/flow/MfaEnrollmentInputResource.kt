package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
data class MfaEnrollmentInputResource(
    @get:Schema(
        description = """
URI the end-user must be returned to once the MFA enrollment completes.
Must be one of the calling client's registered redirect URIs.
        """
    )
    @get:JsonProperty("return_uri")
    val returnUri: String?
)
