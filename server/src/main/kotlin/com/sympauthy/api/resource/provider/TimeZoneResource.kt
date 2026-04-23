package com.sympauthy.api.resource.provider

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
class TimeZoneResource(
    @get:Schema(
        description = "Identifier of the timezone.",
        example = "Europe/France"
    )
    val id: String,
    @get:Schema(
        description = "Offset of the time zone to the Coordinated Universal Time (UTC).Formatted as (+-)HH:mm.",
        example = "+01:00"
    )
    val offset: String
)
