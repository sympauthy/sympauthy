package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CloudflareEdgeTest {

    private val edge = CloudflareEdge()

    @Test
    fun `readIpOrNull - Answer the address the connecting header carries`() {
        assertEquals("203.0.113.7", edge.readIpOrNull(headersOf("CF-Connecting-IP" to "203.0.113.7")))
    }

    @Test
    fun `readGeoOrNull - Answer every location header the managed transform adds`() {
        val geo = edge.readGeoOrNull(
            headersOf(
                "CF-IPCountry" to "US",
                "cf-region-code" to "CA",
                "cf-region" to "California",
                "cf-ipcity" to "Mountain View",
                "cf-postal-code" to "94043",
                "cf-timezone" to "America/Los_Angeles"
            )
        )

        assertEquals(
            SecurityContextGeo(
                countryCode = "US",
                regionCode = "CA",
                region = "California",
                city = "Mountain View",
                postalCode = "94043",
                timeZone = "America/Los_Angeles"
            ),
            geo
        )
    }

    @Test
    fun `readGeoOrNull - Answer the country alone where only IP Geolocation is switched on`() {
        val geo = edge.readGeoOrNull(headersOf("CF-IPCountry" to "US"))

        assertEquals("US", geo?.countryCode)
        assertNull(geo?.city)
    }

    @Test
    fun `readGeoOrNull - Answer no location where neither switch is on`() {
        assertNull(edge.readGeoOrNull(headersOf("CF-Connecting-IP" to "203.0.113.7")))
    }
}
