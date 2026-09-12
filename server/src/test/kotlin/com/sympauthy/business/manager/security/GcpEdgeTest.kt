package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GcpEdgeTest {

    private val edge = GcpEdge()

    @Test
    fun `readIpOrNull - Answer the entry before the one the load balancer appended`() {
        assertEquals("203.0.113.7", edge.readIpOrNull(headersOf("X-Forwarded-For" to "203.0.113.7, 35.191.0.1")))
    }

    @Test
    fun `readIpOrNull - Answer the caller rather than a hop it sent ahead of itself`() {
        val headers = headersOf("X-Forwarded-For" to "10.0.0.1, 203.0.113.7, 35.191.0.1")

        assertEquals("203.0.113.7", edge.readIpOrNull(headers))
    }

    @Test
    fun `readIpOrNull - Answer no address where the header holds only the balancer's own entry`() {
        assertNull(edge.readIpOrNull(headersOf("X-Forwarded-For" to "35.191.0.1")))
    }

    @Test
    fun `readGeoOrNull - Answer the country and the city the custom header packs`() {
        val geo = edge.readGeoOrNull(headersOf("X-Client-Geo-Location" to "US,Mountain View"))

        assertEquals("US", geo?.countryCode)
        assertEquals("Mountain View", geo?.city)
        assertNull(geo?.regionCode)
    }

    @Test
    fun `readGeoOrNull - Answer the country alone where the header carries only one field`() {
        val geo = edge.readGeoOrNull(headersOf("X-Client-Geo-Location" to "US"))

        assertEquals("US", geo?.countryCode)
        assertNull(geo?.city)
    }

    @Test
    fun `readGeoOrNull - Ignore a field beyond the two the header is read as`() {
        val geo = edge.readGeoOrNull(headersOf("X-Client-Geo-Location" to "US,Mountain View,94043"))

        assertEquals("US", geo?.countryCode)
        assertEquals("Mountain View", geo?.city)
        assertNull(geo?.postalCode)
    }

    @Test
    fun `readGeoOrNull - Answer nothing where the custom header did not arrive`() {
        assertNull(edge.readGeoOrNull(headersOf("X-Forwarded-For" to "203.0.113.7, 35.191.0.1")))
    }
}
