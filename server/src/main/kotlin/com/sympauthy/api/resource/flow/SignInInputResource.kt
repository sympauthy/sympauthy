package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
data class SignInInputResource(
    @get:Schema(
        description = """
Login of the end-user.

This value will be matched against the claims collected by this authentication as a first-party
and configured as login claim (see ```auth.login-claims``` configuration).
        """
    )
    val login: String?,
    @get:Schema(
        description = "Password of the end-user."
    )
    val password: String?
)
