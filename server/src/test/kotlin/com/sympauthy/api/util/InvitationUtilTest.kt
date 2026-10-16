package com.sympauthy.api.util

import com.sympauthy.api.exception.LocalizedHttpException
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime

class InvitationUtilTest {

    @Test
    fun `parseInvitationExpiresAt - Read a date and time with no zone`() {
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), parseInvitationExpiresAt("2026-12-01T00:00:00"))
    }

    @Test
    fun `parseInvitationExpiresAt - Answer null where the request named none`() {
        assertNull(parseInvitationExpiresAt(null))
    }

    @ParameterizedTest
    @ValueSource(strings = ["2026-12-01T00:00:00Z", "2026-12-01T00:00:00+02:00", "2026-12-01", "tomorrow"])
    fun `parseInvitationExpiresAt - Refuse a value that is not a date and time with no zone`(value: String) {
        val exception = assertThrows<LocalizedHttpException> {
            parseInvitationExpiresAt(value)
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("invitation.expires_at.malformed", exception.detailsId)
        assertEquals(value, exception.values["value"])
        assertTrue(exception.recoverable)
    }
}
