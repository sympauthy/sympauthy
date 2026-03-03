package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
data class TotpChallengeInputResource(
    @get:Schema(description = "6-digit TOTP code from the authenticator app.")
    val code: String?
)
