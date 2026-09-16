package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "Where the person behind an interactive flow session was observed proving who they are. " +
            "The geo fields are the words of the edge in front of this server, unaltered, and each of them " +
            "is present only where that edge sent it."
)
@Serdeable
data class AdminInteractiveFlowSessionSecurityContextResource(
    @get:Schema(description = "Address the request was observed coming from.")
    val ip: String,
    @get:Schema(description = "User agent the request announced itself with. Absent when none arrived.")
    @get:JsonProperty("user_agent")
    val userAgent: String?,
    @get:Schema(description = "Country the edge placed the address in.")
    @get:JsonProperty("country_code")
    val countryCode: String?,
    @get:Schema(description = "Region code the edge placed the address in.")
    @get:JsonProperty("region_code")
    val regionCode: String?,
    @get:Schema(description = "Region the edge placed the address in.")
    @get:JsonProperty("region")
    val region: String?,
    @get:Schema(description = "City the edge placed the address in.")
    @get:JsonProperty("city")
    val city: String?,
    @get:Schema(description = "Time zone the edge placed the address in.")
    @get:JsonProperty("time_zone")
    val timeZone: String?,
    @get:Schema(
        description = "Date and time (in UTC timezone) at which the observation was taken, which is when the " +
                "credential was proven rather than when the session started."
    )
    @get:JsonProperty("observed_date")
    val observedDate: LocalDateTime
)
