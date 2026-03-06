package com.sympauthy.api.filter

import com.sympauthy.api.AbstractFlowIntegrationTest
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiscoveryCorsFilterTest : AbstractFlowIntegrationTest() {

    private val anyOrigin = "http://some-app.example.com"
    private val discoveryPath = "/.well-known/openid-configuration"
    private val jwksPath = "/.well-known/public.jwk"

    @Test
    fun `OPTIONS preflight returns 200 with wildcard CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(discoveryPath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        assertNotNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS])
        assertNotNull(response.headers[HttpHeaders.ACCESS_CONTROL_MAX_AGE])
    }

    @Test
    fun `GET with Origin header returns wildcard CORS header`() {
        val response = exchange(
            HttpRequest.GET<Any>(discoveryPath).header(HttpHeaders.ORIGIN, anyOrigin)
        )

        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `GET without Origin header does not add CORS headers`() {
        val response = exchange(HttpRequest.GET<Any>(discoveryPath))

        assertNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `JWKS endpoint OPTIONS preflight returns wildcard CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(jwksPath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    private fun exchange(request: HttpRequest<*>): HttpResponse<*> = try {
        httpClient.toBlocking().exchange(request, String::class.java)
    } catch (e: HttpClientResponseException) {
        e.response
    }
}
