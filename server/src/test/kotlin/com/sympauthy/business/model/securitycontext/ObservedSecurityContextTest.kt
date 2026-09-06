package com.sympauthy.business.model.securitycontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ObservedSecurityContextTest {

    @Test
    fun `fingerprint - Answer the same place for two sightings of it`() {
        assertEquals(
            observed(ip = "198.51.100.10", userAgent = "Mozilla/5.0").fingerprint,
            observed(ip = "198.51.100.10", userAgent = "Mozilla/5.0").fingerprint
        )
    }

    @Test
    fun `fingerprint - Ignore where the proxy placed the address`() {
        assertEquals(
            observed(geo = SecurityContextGeo("FR", "OCC", "Toulouse")).fingerprint,
            observed(geo = SecurityContextGeo("FR", "OCC", "Blagnac")).fingerprint
        )
    }

    @Test
    fun `fingerprint - Read two spellings of one address as one place`() {
        assertEquals(
            observed(ip = "2001:DB8::1").fingerprint,
            observed(ip = " 2001:db8::1 ").fingerprint
        )
    }

    @Test
    fun `fingerprint - Keep the case of the user agent apart`() {
        assertNotEquals(
            observed(userAgent = "Mozilla/5.0").fingerprint,
            observed(userAgent = "mozilla/5.0").fingerprint
        )
    }

    @Test
    fun `fingerprint - Keep an address apart from a user agent`() {
        assertNotEquals(
            observed(ip = "a", userAgent = "bc").fingerprint,
            observed(ip = "ab", userAgent = "c").fingerprint
        )
    }

    @Test
    fun `fingerprint - Answer a place of its own where nothing was observed`() {
        assertNotEquals(
            observed(ip = null, userAgent = null).fingerprint,
            observed(ip = "198.51.100.10", userAgent = null).fingerprint
        )
    }

    private fun observed(
        ip: String? = "198.51.100.10",
        userAgent: String? = "Mozilla/5.0",
        geo: SecurityContextGeo = SecurityContextGeo(null, null, null)
    ) = ObservedSecurityContext(ip = ip, userAgent = userAgent, geo = geo)
}
