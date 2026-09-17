package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "One purpose of an interactive flow session, where it stands, and what its handler has to " +
            "say about it."
)
@Serdeable
data class AdminInteractiveFlowSessionPurposeProgressResource(
    @get:Schema(description = "The purpose this entry is about.")
    @get:JsonProperty("purpose")
    val purpose: AdminInteractiveFlowPurposeResource,
    @get:Schema(
        description = "Where this purpose stands. A terminal session has no current purpose; a session that " +
                "was ongoing when it expired still has one.",
        allowableValues = ["completed", "current", "pending"]
    )
    @get:JsonProperty("status")
    val status: String,
    @get:Schema(description = "What this purpose has to say about the session, in the order it is read in.")
    @get:JsonProperty("debug")
    val debug: List<AdminInteractiveFlowSessionDebugInformationResource>
)
