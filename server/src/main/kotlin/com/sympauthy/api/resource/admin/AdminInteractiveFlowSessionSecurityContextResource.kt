package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

private const val ABSENT_UNLESS_LOCATED = "Absent unless the proxy reported it and " +
    "`advanced.security-context.geo` is configured to read it."

@Schema(
    description = "An address and user agent requests of an interactive flow session came from, listed once " +
            "however many requests came from it. The location fields are what the proxy in front of this " +
            "server reported with the latest of those requests, as it sent them."
)
@Serdeable
data class AdminInteractiveFlowSessionSecurityContextResource(
    @get:Schema(
        description = "Address the requests came from. Behind a proxy, it is the proxy's own address unless " +
                "`advanced.security-context.ip` is configured for that proxy."
    )
    @get:JsonProperty("ip")
    val ip: String,
    @get:Schema(description = "User agent the requests sent. Absent when they sent none.")
    @get:JsonProperty("user_agent")
    val userAgent: String?,
    @get:Schema(description = "Country code the proxy reported for the address. $ABSENT_UNLESS_LOCATED")
    @get:JsonProperty("country_code")
    val countryCode: String?,
    @get:Schema(description = "Region code the proxy reported for the address. $ABSENT_UNLESS_LOCATED")
    @get:JsonProperty("region_code")
    val regionCode: String?,
    @get:Schema(description = "Region the proxy reported for the address. $ABSENT_UNLESS_LOCATED")
    @get:JsonProperty("region")
    val region: String?,
    @get:Schema(description = "City the proxy reported for the address. $ABSENT_UNLESS_LOCATED")
    @get:JsonProperty("city")
    val city: String?,
    @get:Schema(
        description = "Time zone the proxy reported for the address, which some proxies send as an " +
                "abbreviation. $ABSENT_UNLESS_LOCATED"
    )
    @get:JsonProperty("time_zone")
    val timeZone: String?,
    @get:Schema(
        description = "Date and time at which the first request from this address and user agent arrived, in UTC."
    )
    @get:JsonProperty("first_seen_date")
    val firstSeenDate: LocalDateTime,
    @get:Schema(
        description = "Date and time at which the latest request from this address and user agent arrived, in UTC."
    )
    @get:JsonProperty("last_seen_date")
    val lastSeenDate: LocalDateTime,
    @get:Schema(
        description = "Number of requests of the interactive flow session that came from this address and user " +
                "agent."
    )
    @get:JsonProperty("observation_count")
    val observationCount: Int,
    @get:Schema(
        description = "Date and time at which the end-user last proved who they are from this address and user " +
                "agent, with a password or a provider, in UTC. Absent when no one did."
    )
    @get:JsonProperty("proven_date")
    val provenDate: LocalDateTime?
)
