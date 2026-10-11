package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A purpose of an interactive flow session, how far the session got with it, and the values " +
            "the session holds for it."
)
@Serdeable
data class AdminInteractiveFlowSessionPurposeProgressResource(
    @get:Schema(description = "The purpose.")
    @get:JsonProperty("purpose")
    val purpose: AdminInteractiveFlowPurposeResource,
    @get:Schema(
        description = "How far the session got with the purpose. `current` is the purpose the end-user is at, " +
                "and no purpose is `current` once the session completed, failed or was cancelled. `pending` " +
                "is a purpose neither completed nor current.",
        allowableValues = ["completed", "current", "pending"]
    )
    @get:JsonProperty("status")
    val status: String,
    @get:Schema(
        description = "Values the session holds for the purpose, labelled for debugging."
    )
    @get:JsonProperty("debug")
    val debug: List<AdminInteractiveFlowSessionDebugInformationResource>
)
