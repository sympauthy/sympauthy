package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReservedIdentifierTest {

    private val ctx = ConfigParsingContext()

    @Test
    fun `refuseReservedIdentifier - Refuse the identifier a capability document already answers for`() {
        ctx.refuseReservedIdentifier("clients.capabilities", "capabilities")

        assertEquals(listOf("config.reserved_identifier"), ctx.errors.map { it.messageId })
        assertEquals(listOf("clients.capabilities"), ctx.errors.map { it.key })
    }

    @Test
    fun `refuseReservedIdentifier - Refuse it whichever case it is written in`() {
        ctx.refuseReservedIdentifier("clients.Capabilities", "Capabilities")

        assertTrue(ctx.hasErrors)
    }

    @Test
    fun `refuseReservedIdentifier - Keep every other identifier`() {
        ctx.refuseReservedIdentifier("clients.capability", "capability")

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `isReservedIdentifier - Answer for the word a capability document already answers for`() {
        assertTrue(isReservedIdentifier("capabilities"))
        assertTrue(isReservedIdentifier("CAPABILITIES"))
        assertFalse(isReservedIdentifier("capability"))
    }
}
