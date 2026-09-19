package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(
    description = "One place an interactive flow session was driven from, deduplicated on the address and " +
            "the user agent rather than appended per request. The geo fields are the words of the edge in " +
            "front of this server, unaltered, and each of them is present only where that edge sent it."
)
@Serdeable
data class AdminInteractiveFlowSessionSecurityContextResource(
    @get:Schema(description = "Address the requests were observed coming from.")
    val ip: String,
    @get:Schema(description = "User agent the requests announced themselves with. Absent when none arrived.")
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
        description = "Date and time (in UTC timezone) at which this place was first seen driving the session."
    )
    @get:JsonProperty("first_seen_date")
    val firstSeenDate: LocalDateTime,
    @get:Schema(
        description = "Date and time (in UTC timezone) at which this place was last seen driving the session."
    )
    @get:JsonProperty("last_seen_date")
    val lastSeenDate: LocalDateTime,
    @get:Schema(
        description = "How many requests of this session came from here. A session driven from one place " +
                "for eleven requests is one entry saying eleven rather than eleven entries."
    )
    @get:JsonProperty("observation_count")
    val observationCount: Int,
    @get:Schema(
        description = "Date and time (in UTC timezone) at which a credential was last proven from here. " +
                "Absent for a place only requests were seen from — which anybody holding the session's " +
                "state can produce, so it is what tells a proven place from a merely observed one."
    )
    @get:JsonProperty("proven_date")
    val provenDate: LocalDateTime?
)
