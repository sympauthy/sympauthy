package com.sympauthy.api.resource.provider

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A time zone the end-user can pick."
)
@Serdeable
class TimeZoneResource(
    @get:Schema(
        description = "Identifier of the time zone, as the IANA database names it.",
        example = "Europe/Paris"
    )
    val id: String,
    @get:Schema(
        description = "Offset of the time zone from UTC at the time of the request, formatted `+HH:mm` or " +
            "`-HH:mm`.",
        example = "+01:00"
    )
    val offset: String
)
