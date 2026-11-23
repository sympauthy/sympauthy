package com.sympauthy.platform.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class LocalDateTimeUtilTest {

    @Test
    fun `min - Return minimum local date time`() {
        assertEquals(
            LocalDateTime.MIN,
            min(LocalDateTime.MAX, LocalDateTime.MIN)
        )
        assertEquals(
            LocalDateTime.MIN,
            min(LocalDateTime.MIN, LocalDateTime.MAX)
        )
        assertEquals(
            LocalDateTime.MIN,
            min(LocalDateTime.MIN, LocalDateTime.MIN)
        )
    }
}
