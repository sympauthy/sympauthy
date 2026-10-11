package com.sympauthy.api.resource.flow

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Login and password the end-user signs in with."
)
@Serdeable
data class SignInInputResource(
    @get:Schema(
        description = "Login of the end-user: the value of any one of the claims `auth.identifier-claims` " +
            "names. When absent, the sign-in is refused with `flow.password.sign_in.invalid`."
    )
    val login: String?,
    @get:Schema(
        description = "Password of the end-user. When absent, the sign-in is refused with " +
            "`flow.password.sign_in.invalid`."
    )
    val password: String?
)
