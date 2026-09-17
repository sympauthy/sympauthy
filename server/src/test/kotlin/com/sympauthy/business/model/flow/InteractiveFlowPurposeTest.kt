package com.sympauthy.business.model.flow

import com.sympauthy.util.wireName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InteractiveFlowPurposeTest {

    @Test
    fun `Every purpose declares a display name`() {
        val undeclared = InteractiveFlowPurpose.entries.filter { it.displayName.isBlank() }

        assertTrue(undeclared.isEmpty(), "these purposes declare no display name: $undeclared")
    }

    @Test
    fun `Every purpose declares a display name the wire name does not already spell`() {
        val derivable = InteractiveFlowPurpose.entries.filter {
            it.displayName.equals(it.wireName.replace("_", " "), ignoreCase = true)
        }

        assertTrue(derivable.isEmpty(), "these display names say no more than the wire name: $derivable")
    }

    @Test
    fun `No two purposes share a display name`() {
        val names = InteractiveFlowPurpose.entries.map { it.displayName }

        assertEquals(names.size, names.toSet().size, "two purposes are labelled the same: $names")
    }
}
