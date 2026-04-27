package com.sympauthy.config

import com.sympauthy.config.exception.ConfigurationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConfigParsingContextTest {

    @Test
    fun `parse - Returns value on success`() {
        val ctx = ConfigParsingContext()
        val result = ctx.parse { "hello" }
        assertEquals("hello", result)
        assertFalse(ctx.hasErrors)
        assertTrue(ctx.errors.isEmpty())
    }

    @Test
    fun `parse - Returns null and accumulates error on ConfigurationException`() {
        val ctx = ConfigParsingContext()
        val result = ctx.parse {
            throw ConfigurationException("test.key", "config.missing")
        }
        assertNull(result)
        assertTrue(ctx.hasErrors)
        assertEquals(1, ctx.errors.size)
        assertEquals("config.missing", ctx.errors[0].messageId)
    }

    @Test
    fun `parse - Accumulates multiple errors`() {
        val ctx = ConfigParsingContext()
        ctx.parse { throw ConfigurationException("key1", "config.missing") }
        ctx.parse { throw ConfigurationException("key2", "config.empty") }
        val result = ctx.parse { 42 }

        assertEquals(42, result)
        assertEquals(2, ctx.errors.size)
    }

    @Test
    fun `addError - Adds validation error`() {
        val ctx = ConfigParsingContext()
        ctx.addError(ConfigurationException("key", "config.invalid"))
        assertTrue(ctx.hasErrors)
        assertEquals(1, ctx.errors.size)
    }

    @Test
    fun `child and merge - Isolates and merges errors`() {
        val parent = ConfigParsingContext()
        parent.addError(ConfigurationException("parent.key", "config.parent"))

        val child = parent.child()
        child.addError(ConfigurationException("child.key", "config.child"))

        assertEquals(1, parent.errors.size)
        assertEquals(1, child.errors.size)

        parent.merge(child)
        assertEquals(2, parent.errors.size)
    }

    @Test
    fun `child hasErrors - Does not affect parent until merge`() {
        val parent = ConfigParsingContext()
        val child = parent.child()
        child.addError(ConfigurationException("child.key", "config.child"))

        assertFalse(parent.hasErrors)
        assertTrue(child.hasErrors)

        parent.merge(child)
        assertTrue(parent.hasErrors)
    }

    @Test
    fun `errors - Returns immutable copy`() {
        val ctx = ConfigParsingContext()
        val errors1 = ctx.errors
        ctx.addError(ConfigurationException("key", "config.test"))
        val errors2 = ctx.errors

        assertEquals(0, errors1.size)
        assertEquals(1, errors2.size)
    }
}
