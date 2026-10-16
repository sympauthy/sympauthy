package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.provider.TimeZoneResource
import com.sympauthy.business.model.user.claim.TimeZone
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Singleton
class TimeZoneResourceMapper {

    private val offsetFormatter = DateTimeFormatter.ofPattern("xxx")

    fun toResources(timeZones: List<TimeZone>): List<TimeZoneResource> {
        val now = LocalDateTime.now()
        return timeZones
            .map {
                toResource(
                    timeZone = it,
                    now = now
                )
            }
            .sortedBy(TimeZoneResource::id)
    }

    fun toResource(
        timeZone: TimeZone,
        now: LocalDateTime
    ): TimeZoneResource {
        return TimeZoneResource(
            id = timeZone.zone.id,
            offset = toOffset(timeZone, now),
        )
    }

    fun toOffset(
        timeZone: TimeZone,
        now: LocalDateTime
    ): String {
        return timeZone.zone.rules.getOffset(now)
            .let(offsetFormatter::format)
    }
}
