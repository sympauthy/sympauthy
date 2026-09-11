package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GcpEdgeProviderTest {

    private val provider = GcpEdgeProvider()

    @Test
    fun `read - Answer the entry before the one the load balancer appended`() {
        val observed = provider.read(headersOf("X-Forwarded-For" to "203.0.113.7, 35.191.0.1"))

        assertEquals("203.0.113.7", observed.ipAddress)
    }

    @Test
    fun `read - Answer the caller rather than a hop it sent ahead of itself`() {
        val observed = provider.read(headersOf("X-Forwarded-For" to "10.0.0.1, 203.0.113.7, 35.191.0.1"))

        assertEquals("203.0.113.7", observed.ipAddress)
    }

    @Test
    fun `read - Answer no address where the header holds only the balancer's own entry`() {
        val observed = provider.read(headersOf("X-Forwarded-For" to "35.191.0.1"))

        assertNull(observed.ipAddress)
    }

    @Test
    fun `read - Answer the country and the city the custom header packs`() {
        val observed = provider.read(headersOf("X-Client-Geo-Location" to "US,Mountain View"))

        assertEquals("US", observed.geo?.countryCode)
        assertEquals("Mountain View", observed.geo?.city)
        assertNull(observed.geo?.regionCode)
    }

    @Test
    fun `read - Answer the country alone where the header carries only one field`() {
        val observed = provider.read(headersOf("X-Client-Geo-Location" to "US"))

        assertEquals("US", observed.geo?.countryCode)
        assertNull(observed.geo?.city)
    }

    @Test
    fun `read - Ignore a field beyond the two the header is read as`() {
        val observed = provider.read(headersOf("X-Client-Geo-Location" to "US,Mountain View,94043"))

        assertEquals("US", observed.geo?.countryCode)
        assertEquals("Mountain View", observed.geo?.city)
        assertNull(observed.geo?.postalCode)
    }

    @Test
    fun `read - Answer no location where the custom header did not arrive`() {
        val observed = provider.read(headersOf("X-Forwarded-For" to "203.0.113.7, 35.191.0.1"))

        assertNull(observed.geo)
    }
}
