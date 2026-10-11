package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.STEP_REDIRECT_URL_DESCRIPTION
import com.sympauthy.api.util.STEP_URL_STATE_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Claims the claims step collects from the end-user, or the `redirect_url` to send the " +
        "browser to instead. " + STEP_URL_STATE_DESCRIPTION
)
@Serdeable
data class ClaimsFlowResource(
    @get:Schema(
        description = "Claims to ask the end-user for. Absent when `redirect_url` is present."
    )
    val claims: List<ClaimValueResource>? = null,

    @get:Schema(
        name = "redirect_url",
        description = STEP_REDIRECT_URL_DESCRIPTION + " Absent when the step has claims to collect."
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)

@Schema(
    description = "A claim the claims step asks the end-user for, with the value the user holds and the one a " +
        "provider suggests."
)
@Serdeable
data class ClaimValueResource(
    @get:Schema(
        description = "Identifier of the claim."
    )
    val id: String,
    @get:Schema(
        description = "Whether the end-user must provide a value for this claim before the flow completes."
    )
    val required: Boolean,
    @get:Schema(
        description = "Name of the claim to show the end-user, in the language the request asks for, or " +
            "its identifier when no name is defined for it."
    )
    val name: String,
    @get:Schema(
        description = "Data type of the claim's value.",
        allowableValues = ["boolean", "date", "email", "number", "phone_number", "string", "timezone"]
    )
    val type: String,
    @get:Schema(
        description = "Group of related claims this claim belongs to. Absent when it belongs to none.",
        allowableValues = ["identity", "address"]
    )
    val group: String?,
    @get:Schema(
        description = "Whether the user already holds this claim, with or without a value. A claim the " +
            "end-user was asked for and left empty is held without one."
    )
    val collected: Boolean,
    @get:Schema(
        description = "Value the user holds for this claim. Absent when the user does not hold the claim, or " +
            "holds it without a value."
    )
    val value: Any?,
    @get:Schema(
        description = "Value a provider linked to the user asserts for this claim, to offer the end-user as a " +
            "default they may change. The most recently refreshed provider asserting one wins. Absent when no " +
            "provider linked to the user asserts one."
    )
    @get:JsonProperty("suggested_value")
    val suggestedValue: Any? = null
)
