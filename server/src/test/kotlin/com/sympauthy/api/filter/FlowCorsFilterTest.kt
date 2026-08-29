package com.sympauthy.api.filter

import com.sympauthy.api.AbstractFlowIntegrationTest
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FlowCorsFilterTest : AbstractFlowIntegrationTest() {

    // Origin matching urls.root from application-test.yml — allowed by the default flow
    private val allowedOrigin = "http://localhost:18080"
    private val unknownOrigin = "http://evil.example.com"

    // Both the GET (configuration) and POST (sign-in) endpoints live on SignInController.
    private val path = "/api/v1/flow/sign-in"

    /** Mandatory headers, then cors.allowed-headers as declared in application-default.yml. */
    private val expectedAllowedHeaders = "Content-Type, Authorization, DPoP, X-Requested-With"

    @Test
    fun `OPTIONS preflight with allowed origin returns 200 with full CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(path)
            .header(HttpHeaders.ORIGIN, allowedOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals(allowedOrigin, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        assertNotNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS])
        assertEquals(expectedAllowedHeaders, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS])
        assertNotNull(response.headers[HttpHeaders.ACCESS_CONTROL_MAX_AGE])
    }

    @Test
    fun `OPTIONS preflight with unknown origin returns 200 without CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(path)
            .header(HttpHeaders.ORIGIN, unknownOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `GET with allowed origin appends CORS headers to response`() {
        val response = exchange(HttpRequest.GET<Any>(path).header(HttpHeaders.ORIGIN, allowedOrigin))

        assertEquals(allowedOrigin, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        assertTrue(response.headers.getAll(HttpHeaders.VARY).any { HttpHeaders.ORIGIN in it })
    }

    @Test
    fun `GET with unknown origin does not append CORS headers to response`() {
        val response = exchange(HttpRequest.GET<Any>(path).header(HttpHeaders.ORIGIN, unknownOrigin))

        assertNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `GET without Origin header does not append CORS headers to response`() {
        val response = exchange(HttpRequest.GET<Any>(path))

        assertNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    private fun exchange(request: HttpRequest<*>): HttpResponse<*> = try {
        httpClient.toBlocking().exchange(request, String::class.java)
    } catch (e: HttpClientResponseException) {
        e.response
    }
}
