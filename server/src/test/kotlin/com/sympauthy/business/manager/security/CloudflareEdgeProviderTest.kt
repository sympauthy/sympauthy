package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CloudflareEdgeProviderTest {

    private val provider = CloudflareEdgeProvider()

    @Test
    fun `read - Answer the address and every location header the managed transform adds`() {
        val observed = provider.read(
            headersOf(
                "CF-Connecting-IP" to "203.0.113.7",
                "CF-IPCountry" to "US",
                "cf-region-code" to "CA",
                "cf-region" to "California",
                "cf-ipcity" to "Mountain View",
                "cf-postal-code" to "94043",
                "cf-timezone" to "America/Los_Angeles"
            )
        )

        assertEquals("203.0.113.7", observed.ipAddress)
        assertEquals(
            SecurityContextGeo(
                countryCode = "US",
                regionCode = "CA",
                region = "California",
                city = "Mountain View",
                postalCode = "94043",
                timeZone = "America/Los_Angeles"
            ),
            observed.geo
        )
    }

    @Test
    fun `read - Answer the country alone where only IP Geolocation is switched on`() {
        val observed = provider.read(
            headersOf("CF-Connecting-IP" to "203.0.113.7", "CF-IPCountry" to "US")
        )

        assertEquals("US", observed.geo?.countryCode)
        assertNull(observed.geo?.city)
    }

    @Test
    fun `read - Answer no location where neither switch is on`() {
        val observed = provider.read(headersOf("CF-Connecting-IP" to "203.0.113.7"))

        assertEquals("203.0.113.7", observed.ipAddress)
        assertNull(observed.geo)
    }
}
