package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class PositiveDurationValidationTest {

    @Test
    fun `validatePositiveDuration - Refuse a duration that is not greater than zero`() {
        listOf(Duration.ZERO, Duration.ofHours(-1)).forEach { duration ->
            val ctx = ConfigParsingContext()

            assertNull(validatePositiveDuration(ctx, "auth.grant.expiration", duration))

            val error = ctx.errors.single()
            assertEquals("auth.grant.expiration", error.key)
            assertEquals("config.duration.not_positive", error.messageId)
            assertEquals(duration.toString(), error.values["duration"])
        }
    }

    @Test
    fun `validatePositiveDuration - Accept a duration greater than zero`() {
        val ctx = ConfigParsingContext()

        assertEquals(
            Duration.ofSeconds(1),
            validatePositiveDuration(ctx, "auth.grant.expiration", Duration.ofSeconds(1))
        )

        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `validatePositiveDuration - Accept a duration left unset`() {
        val ctx = ConfigParsingContext()

        assertNull(validatePositiveDuration(ctx, "auth.grant.expiration", null))

        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }
}
