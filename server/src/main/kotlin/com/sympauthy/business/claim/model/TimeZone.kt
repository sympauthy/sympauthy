package com.sympauthy.business.claim.model

import java.time.ZoneId

/**
 * A timezone known by the TZ database of the authorization server.
 */
data class TimeZone(
    /**
     * Identifier of the timezone.
     */
    val zone: ZoneId
)
