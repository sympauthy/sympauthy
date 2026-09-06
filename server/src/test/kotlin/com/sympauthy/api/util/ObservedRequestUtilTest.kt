package com.sympauthy.api.util

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.simple.SimpleHttpRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObservedRequestUtilTest {

    @Test
    fun `observedRequest - Carry the address the socket connected from`() {
        val request = requestFrom(peer = "198.51.100.10")

        assertEquals("198.51.100.10", request.observedRequest().peer)
    }

    @Test
    fun `observedRequest - Carry the headers the request arrived with`() {
        val request = requestFrom(peer = "10.0.0.1")
        request.headers.add("X-Real-IP", "198.51.100.10")

        assertEquals("198.51.100.10", request.observedRequest().headerOrNull("X-Real-IP"))
    }

    @Test
    fun `observedRequest - Carry every line a header arrived on`() {
        val request = requestFrom(peer = "10.0.0.1")
        request.headers.add("X-Forwarded-For", "203.0.113.9")
        request.headers.add("X-Forwarded-For", "198.51.100.10")

        assertEquals(
            listOf("203.0.113.9", "198.51.100.10"),
            request.observedRequest().headersOf("X-Forwarded-For")
        )
    }

    /**
     * The peer is spelled into the request's own url because that is where [HttpRequest] takes an
     * address it was not given from.
     */
    private fun requestFrom(peer: String) = SimpleHttpRequest<Any>(HttpMethod.GET, "http://$peer:8080/", null)
}
