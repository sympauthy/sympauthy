package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "MFA methods the end-user can choose between, or the `redirect_url` of the one MFA " +
        "method's step instead. " + STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class MfaFlowResource(
    @get:Schema(
        description = "URL to send the browser to instead of rendering this step: the step of the one MFA " +
            "method there is to choose. Absent when `methods` is present."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null,
    @get:Schema(
        description = "MFA methods the end-user can choose between. Absent when `redirect_url` is present."
    )
    val methods: List<MfaMethodResource>? = null,
    @get:Schema(
        description = "URL that skips the MFA enrollment and continues the flow. Absent unless the step is an " +
            "optional enrollment: `mfa.required` is `false`, and no client or administrator asked for it."
    )
    @get:JsonProperty("skip_redirect_url")
    val skipRedirectUrl: String? = null
)

@Schema(
    description = "An MFA method the end-user can choose."
)
@Serdeable
data class MfaMethodResource(
    @get:Schema(
        description = "Type of the MFA method.",
        allowableValues = ["TOTP"]
    )
    val method: String,
    @get:Schema(
        description = "URL of the step enrolling or challenging this MFA method."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String
)
