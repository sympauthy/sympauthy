package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaddyEdgeProviderTest {

    private val provider = CaddyEdgeProvider()

    @Test
    fun `read - Answer the entry Caddy appended rather than the ones that arrived with the request`() {
        val observed = provider.read(headersOf("X-Forwarded-For" to "10.0.0.1, 198.51.100.4, 203.0.113.7"))

        assertEquals("203.0.113.7", observed.ipAddress)
    }

    @Test
    fun `read - Answer the last entry across several headers a chain of proxies wrote`() {
        val observed = provider.read(
            headersOf(
                "X-Forwarded-For" to "10.0.0.1",
                "X-Forwarded-For" to "198.51.100.4, 203.0.113.7"
            )
        )

        assertEquals("203.0.113.7", observed.ipAddress)
    }

    @Test
    fun `read - Answer the only entry where nothing sits in front of Caddy`() {
        val observed = provider.read(headersOf("X-Forwarded-For" to "203.0.113.7"))

        assertEquals("203.0.113.7", observed.ipAddress)
    }

    @Test
    fun `read - Answer nothing where the header did not arrive`() {
        val observed = provider.read(headersOf("X-Real-IP" to "203.0.113.7"))

        assertNull(observed.ipAddress)
        assertNull(observed.geo)
    }
}
