package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "One thing a purpose has to say about where a session stands. The display name is a label " +
            "written for a person to read: it may be reworded in any release, so nothing may switch on it."
)
@Serdeable
data class AdminInteractiveFlowSessionDebugInformationResource(
    @get:Schema(description = "What this value is, phrased for a person.")
    @get:JsonProperty("display_name")
    val displayName: String,
    @get:Schema(
        description = "The value as it stands. Absent where the field exists and holds nothing; the entry " +
                "itself is never omitted."
    )
    @get:JsonProperty("value")
    val value: String?
)
