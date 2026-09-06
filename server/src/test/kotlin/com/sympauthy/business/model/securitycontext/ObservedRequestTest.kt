package com.sympauthy.business.model.securitycontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ObservedRequestTest {

    @Test
    fun `headerOrNull - Read a header written in another case`() {
        val request = observedRequestOf(headers = arrayOf("X-Real-Ip" to "198.51.100.10"))

        assertEquals("198.51.100.10", request.headerOrNull("X-Real-IP"))
    }

    @Test
    fun `headerOrNull - Answer null for a header that did not arrive`() {
        assertNull(observedRequestOf().headerOrNull("X-Real-IP"))
    }

    @Test
    fun `headerOrNull - Answer null for a header that arrived empty`() {
        val request = observedRequestOf(headers = arrayOf("X-Real-IP" to ""))

        assertNull(request.headerOrNull("X-Real-IP"))
    }

    @Test
    fun `headersOf - Keep every value a header arrived with`() {
        val request = ObservedRequest(
            peer = null,
            headers = mapOf("X-Forwarded-For" to listOf("203.0.113.9", "198.51.100.10"))
        )

        assertEquals(listOf("203.0.113.9", "198.51.100.10"), request.headersOf("x-forwarded-for"))
    }
}
