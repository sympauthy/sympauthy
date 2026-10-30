package com.sympauthy.business.model.grant

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class GrantRevocationCriteriaTest {

    @Test
    fun `GrantRevocationCriteria - Refuse criteria giving no set, which would reach every grant`() {
        assertThrows<IllegalArgumentException> { GrantRevocationCriteria() }
    }

    @Test
    fun `GrantRevocationCriteria - Accept an empty set, which reaches no grant`() {
        assertDoesNotThrow { GrantRevocationCriteria(clientIds = emptySet()) }
    }
}
