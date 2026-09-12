package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaddyEdgeTest {

    private val edge = CaddyEdge()

    @Test
    fun `readIpOrNull - Answer the entry Caddy appended rather than the ones that arrived with the request`() {
        val headers = headersOf("X-Forwarded-For" to "10.0.0.1, 198.51.100.4, 203.0.113.7")

        assertEquals("203.0.113.7", edge.readIpOrNull(headers))
    }

    @Test
    fun `readIpOrNull - Answer the last entry across several headers a chain of proxies wrote`() {
        val headers = headersOf(
            "X-Forwarded-For" to "10.0.0.1",
            "X-Forwarded-For" to "198.51.100.4, 203.0.113.7"
        )

        assertEquals("203.0.113.7", edge.readIpOrNull(headers))
    }

    @Test
    fun `readIpOrNull - Answer the only entry where nothing sits in front of Caddy`() {
        assertEquals("203.0.113.7", edge.readIpOrNull(headersOf("X-Forwarded-For" to "203.0.113.7")))
    }

    @Test
    fun `readIpOrNull - Answer nothing where the header did not arrive`() {
        assertNull(edge.readIpOrNull(headersOf("X-Real-IP" to "203.0.113.7")))
    }
}
