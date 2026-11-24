package com.sympauthy.api.flow.resource

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.flow.util.STEP_CANCEL_URL_DESCRIPTION
import com.sympauthy.api.flow.util.STEP_CLIENT_DESCRIPTION
import com.sympauthy.api.flow.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.flow.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "Configuration of the step where the end-user enters the code sent to reset their password, " +
        "or the `redirect_url` to send the browser to instead. The step is the same whether or not a user " +
        "holds the login, and whether or not a code was sent. " + STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class ResetPasswordValidationFlowResource(
    @get:Schema(
        description = "Login the end-user asked to reset the password of, as they entered it. Absent when " +
            "`redirect_url` is present."
    )
    @get:JsonProperty("login")
    val login: String? = null,
    @get:Schema(
        name = "resend_date",
        description = "Date and time at which a new code can be asked for, in UTC. Absent when " +
            "`advanced.validation-code.resend-delay` is not configured, in which case a new one can be asked " +
            "for only once the code has expired, and when `redirect_url` is present.",
        example = "2026-01-01T10:43:30"
    )
    @get:JsonProperty("resend_date")
    val resendDate: LocalDateTime? = null,
    @get:Schema(
        description = STEP_CLIENT_DESCRIPTION + " Absent too when `redirect_url` is present."
    )
    @get:JsonProperty("client")
    val client: ClientReferenceResource? = null,
    @get:Schema(
        name = "cancel_url",
        description = STEP_CANCEL_URL_DESCRIPTION + " Absent too when `redirect_url` is present."
    )
    @get:JsonProperty("cancel_url")
    val cancelUrl: String? = null,
    @get:Schema(
        name = "redirect_url",
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when the end-user is expected to enter a code on " +
            "this step."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
