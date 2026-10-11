package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "TOTP code the end-user entered to confirm the enrollment."
)
@Serdeable
data class TotpEnrollInputResource(
    @get:Schema(
        description = "Six-digit code the end-user's authenticator shows. When absent, the enrollment is " +
            "refused with `flow.mfa.totp.enroll.invalid_code`."
    )
    val code: String?
)
