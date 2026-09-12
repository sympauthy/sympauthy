package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CloudFrontEdgeTest {

    private val edge = CloudFrontEdge()

    @Test
    fun `readIpOrNull - Answer the viewer address without the source port beside it`() {
        assertEquals(
            "198.51.100.10",
            edge.readIpOrNull(headersOf("CloudFront-Viewer-Address" to "198.51.100.10:46532"))
        )
    }

    @Test
    fun `readIpOrNull - Keep every group of an IPv6 address but the port`() {
        assertEquals(
            "2001:db8:85a3::8a2e:370:7334",
            edge.readIpOrNull(headersOf("CloudFront-Viewer-Address" to "2001:db8:85a3::8a2e:370:7334:46532"))
        )
    }

    @Test
    fun `readIpOrNull - Drop the brackets an IPv6 address is written in`() {
        assertEquals("2001:db8::1", edge.readIpOrNull(headersOf("CloudFront-Viewer-Address" to "[2001:db8::1]:46532")))
    }

    @Test
    fun `readIpOrNull - Leave an IPv4 address carrying no port alone`() {
        assertEquals("198.51.100.10", edge.readIpOrNull(headersOf("CloudFront-Viewer-Address" to "198.51.100.10")))
    }

    @Test
    fun `readGeoOrNull - Answer every location header the distribution forwarded`() {
        val geo = edge.readGeoOrNull(
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
            geo
        )
    }

    @Test
    fun `readGeoOrNull - Answer nothing where the origin request policy forwards none of the headers`() {
        assertNull(edge.readGeoOrNull(headersOf("X-Real-IP" to "203.0.113.7")))
    }
}
