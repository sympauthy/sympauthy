package com.sympauthy.api.util

import com.sympauthy.api.exception.recoverableHttpExceptionOf
import io.micronaut.http.HttpStatus.BAD_REQUEST
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * The date and time [expiresAt] spells, or null where the request named none.
 *
 * Throws a recoverable `400` `invitation.expires_at.malformed` where [expiresAt] is not ISO-8601 with no
 * zone, the form every date on the API takes. A value carrying a zone or an offset is refused rather than
 * converted.
 */
fun parseInvitationExpiresAt(expiresAt: String?): LocalDateTime? {
    if (expiresAt == null) return null
    return try {
        LocalDateTime.parse(expiresAt)
    } catch (_: DateTimeParseException) {
        throw recoverableHttpExceptionOf(
            BAD_REQUEST, "invitation.expires_at.malformed", "description.invitation.expires_at.malformed",
            "value" to expiresAt
        )
    }
}
