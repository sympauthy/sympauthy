package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Action the confirmation step asks the end-user to approve, or the `redirect_url` to send " +
        "the browser to instead. " + STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class ConfirmFlowResource(
    @get:Schema(
        description = "Action a client or an administrator asked for on the end-user's behalf. Absent when " +
            "`redirect_url` is present.",
        allowableValues = ["ENROLL_MFA", "LINK_PROVIDER"]
    )
    val action: String? = null,
    @get:Schema(
        name = "requires_reauthentication",
        description = "Whether the end-user is asked to sign in again after approving, so the UI can warn them " +
            "beforehand. `false` when `redirect_url` is present."
    )
    @get:JsonProperty("requires_reauthentication")
    val requiresReauthentication: Boolean = false,
    @get:Schema(
        name = "initiating_client_id",
        description = "Identifier of the client that asked for the action. Absent when an administrator asked " +
            "for it, or when `redirect_url` is present."
    )
    @get:JsonProperty("initiating_client_id")
    val initiatingClientId: String? = null,
    @get:Schema(
        name = "redirect_url",
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when an action is waiting for the end-user's " +
            "approval."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
