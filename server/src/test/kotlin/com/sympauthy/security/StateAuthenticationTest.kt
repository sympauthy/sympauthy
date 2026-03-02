package com.sympauthy.security

import com.sympauthy.security.SecurityRule.HAS_STATE
import io.micronaut.security.authentication.Authentication
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class StateAuthenticationTest {

    @Test
    fun `getRoles returns HAS_STATE when state is non-null`() {
        val auth = StateAuthentication("some-jwt")

        assertTrue(auth.roles.contains(HAS_STATE))
    }

    @Test
    fun `getRoles returns empty collection when state is null`() {
        val auth = StateAuthentication(null)

        assertTrue(auth.roles.isEmpty())
    }

    @Test
    fun `stateOrNull returns state when instance is StateAuthentication`() {
        val state = "some-jwt"
        val auth = StateAuthentication(state)

        assertEquals(state, auth.stateOrNull)
    }

    @Test
    fun `stateOrNull returns null when instance is not StateAuthentication`() {
        val auth = mockk<Authentication>()

        assertNull(auth.stateOrNull)
    }
}
