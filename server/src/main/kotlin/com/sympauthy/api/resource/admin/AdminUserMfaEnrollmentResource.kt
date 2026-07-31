package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Response from starting an admin-initiated MFA enrollment: the link to hand to the user."
)
@Serdeable
data class AdminUserMfaEnrollmentResource(
    @get:Schema(
        description = """
URL to hand or send to the end-user so they can enroll their authenticator. The signed ```state```
identifying the enrollment session is already included as a query parameter.
        """
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String
)
