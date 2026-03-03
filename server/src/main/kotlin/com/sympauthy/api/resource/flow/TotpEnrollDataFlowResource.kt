package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Data required by the end-user to enroll a TOTP authenticator app."
)
@Serdeable
data class TotpEnrollDataFlowResource(
    @get:Schema(
        name = "uri",
        description = """
Standard otpauth:// URI encoding the issuer, account and secret.
Can be rendered as a QR code for scanning with an authenticator app.
        """
    )
    @get:JsonProperty("uri")
    val uri: String,

    @get:Schema(
        name = "secret",
        description = "Base32-encoded TOTP secret for manual entry into an authenticator app."
    )
    @get:JsonProperty("secret")
    val secret: String
)
