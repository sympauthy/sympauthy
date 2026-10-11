package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Secret of a TOTP enrollment, in the two forms an authenticator takes."
)
@Serdeable
data class TotpEnrollDataFlowResource(
    @get:Schema(
        name = "uri",
        description = "URL of the `otpauth` scheme carrying the issuer, a label naming the user and the " +
            "secret, to render as a QR code for an authenticator to scan."
    )
    @get:JsonProperty("uri")
    val uri: String,

    @get:Schema(
        name = "secret",
        description = "Secret, encoded in Base32, for an end-user typing it into an authenticator."
    )
    @get:JsonProperty("secret")
    val secret: String
)
