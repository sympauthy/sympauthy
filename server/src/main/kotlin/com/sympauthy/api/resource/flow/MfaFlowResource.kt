package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Response from the MFA router endpoint.

Exactly one of the two shapes is returned:
- **Auto-redirect**: only `redirect_url` is present. The UI must follow it immediately without showing any screen.
- **Method selection**: `methods` is present. The UI must render a selection screen listing the available methods.
  When `skip_redirect_url` is also present, a skip button must be shown alongside the method list.
    """
)
@Serdeable
data class MfaFlowResource(
    @get:Schema(
        description = "URL to follow immediately when no user interaction is needed (auto-redirect case)."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null,
    @get:Schema(
        description = "List of MFA methods available for the end-user to choose from."
    )
    val methods: List<MfaMethodResource>? = null,
    @get:Schema(
        description = """
URL to navigate to in order to skip the MFA step.
Only present when MFA is optional (`mfa.required=false`).
        """
    )
    @get:JsonProperty("skip_redirect_url")
    val skipRedirectUrl: String? = null
)

@Serdeable
data class MfaMethodResource(
    @get:Schema(
        description = "Identifier of the MFA method. Currently only TOTP is supported."
    )
    val method: String,
    @get:JsonProperty("redirect_url")
    val redirectUrl: String
)
