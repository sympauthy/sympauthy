package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "One value an interactive flow session holds for a purpose, labelled for debugging."
)
@Serdeable
data class AdminInteractiveFlowSessionDebugInformationResource(
    @get:Schema(
        description = "Label naming the value, in English. It may be reworded in any release."
    )
    @get:JsonProperty("display_name")
    val displayName: String,
    @get:Schema(
        description = "The value. Absent when it is not set; the entry is listed all the same."
    )
    @get:JsonProperty("value")
    val value: String?
)
