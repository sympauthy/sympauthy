package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "TOTP code the end-user entered to pass the MFA challenge."
)
@Serdeable
data class TotpChallengeInputResource(
    @get:Schema(
        description = "Six-digit code the end-user's authenticator shows. When absent, the code is refused " +
            "with `flow.mfa.totp.challenge.invalid_code`."
    )
    val code: String?
)
