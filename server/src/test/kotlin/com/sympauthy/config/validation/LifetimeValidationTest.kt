package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class LifetimeValidationTest {

    @Test
    fun `validateLifetime - Refuse a lifetime that is not greater than zero`() {
        listOf(Duration.ZERO, Duration.ofHours(-1)).forEach { lifetime ->
            val ctx = ConfigParsingContext()

            assertNull(validateLifetime(ctx, "auth.grant.expiration", lifetime))

            val error = ctx.errors.single()
            assertEquals("auth.grant.expiration", error.key)
            assertEquals("config.lifetime.not_positive", error.messageId)
            assertEquals(lifetime.toString(), error.values["duration"])
        }
    }

    @Test
    fun `validateLifetime - Accept a lifetime greater than zero`() {
        val ctx = ConfigParsingContext()

        assertEquals(Duration.ofSeconds(1), validateLifetime(ctx, "auth.grant.expiration", Duration.ofSeconds(1)))

        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }

    @Test
    fun `validateLifetime - Accept a lifetime left unset`() {
        val ctx = ConfigParsingContext()

        assertNull(validateLifetime(ctx, "auth.grant.expiration", null))

        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
    }
}
