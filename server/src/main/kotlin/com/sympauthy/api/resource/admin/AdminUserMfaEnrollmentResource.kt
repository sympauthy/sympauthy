package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "MFA enrollment started for a user, and where to send the end-user."
)
@Serdeable
data class AdminUserMfaEnrollmentResource(
    @get:Schema(
        description = "URL of the first step of the flow, to send the end-user's browser to."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String
)
