package com.sympauthy.security

import com.sympauthy.security.AccessTokenScheme.BEARER
import com.sympauthy.security.AccessTokenScheme.DPOP
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccessTokenSchemeTest {

    @Test
    fun `accessTokenSchemeOrNull - Read DPoP off a token bound to a key`() {
        val authentication = mockk<UserAuthentication> {
            every { authenticationToken } returns mockk { every { dpopJkt } returns "thumbprint" }
        }

        assertEquals(DPOP, authentication.accessTokenSchemeOrNull)
    }

    @Test
    fun `accessTokenSchemeOrNull - Read Bearer off a token bound to no key`() {
        val authentication = mockk<ClientAuthentication> {
            every { authenticationToken } returns mockk { every { dpopJkt } returns null }
        }

        assertEquals(BEARER, authentication.accessTokenSchemeOrNull)
    }

    @Test
    fun `accessTokenSchemeOrNull - Return null for an authentication made from the state`() {
        assertNull(StateAuthentication("state").accessTokenSchemeOrNull)
    }
}
