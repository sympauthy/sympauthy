package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CloudFrontEdgeProviderTest {

    private val provider = CloudFrontEdgeProvider()

    @Test
    fun `read - Answer the viewer address without the source port beside it`() {
        val observed = provider.read(headersOf("CloudFront-Viewer-Address" to "198.51.100.10:46532"))

        assertEquals("198.51.100.10", observed.ipAddress)
    }

    @Test
    fun `read - Keep every group of an IPv6 address but the port`() {
        val observed = provider.read(headersOf("CloudFront-Viewer-Address" to "2001:db8:85a3::8a2e:370:7334:46532"))

        assertEquals("2001:db8:85a3::8a2e:370:7334", observed.ipAddress)
    }

    @Test
    fun `read - Drop the brackets an IPv6 address is written in`() {
        val observed = provider.read(headersOf("CloudFront-Viewer-Address" to "[2001:db8::1]:46532"))

        assertEquals("2001:db8::1", observed.ipAddress)
    }

    @Test
    fun `read - Answer every location header the distribution forwarded`() {
        val observed = provider.read(
            headersOf(
                "CloudFront-Viewer-Country" to "US",
                "CloudFront-Viewer-Country-Region" to "CA",
                "CloudFront-Viewer-Country-Region-Name" to "California",
                "CloudFront-Viewer-City" to "Mountain View",
                "CloudFront-Viewer-Postal-Code" to "94043",
                "CloudFront-Viewer-Time-Zone" to "America/Los_Angeles"
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
            observed.geo
        )
    }

    @Test
    fun `read - Answer nothing where the origin request policy forwards none of the headers`() {
        val observed = provider.read(headersOf("X-Real-IP" to "203.0.113.7"))

        assertNull(observed.ipAddress)
        assertNull(observed.geo)
    }
}
