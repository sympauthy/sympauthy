package com.sympauthy.api.filter

import com.sympauthy.api.AbstractFlowIntegrationTest
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UserCorsFilterTest : AbstractFlowIntegrationTest() {

    private val anyOrigin = "http://some-app.example.com"
    private val localOrigin = "http://localhost:5173"
    private val claimsPath = "/api/v1/user/claims"

    /** Mandatory headers, then cors.allowed-headers as declared in application-default.yml. */
    private val expectedAllowedHeaders = "Content-Type, Authorization, DPoP, X-Requested-With"

    @Test
    fun `OPTIONS preflight returns 200 with wildcard CORS headers and the methods of the surface`() {
        val request = HttpRequest.OPTIONS<Any>(claimsPath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PATCH.name)

        // No Authorization header: the surface is @Secured(IS_USER), so an unfiltered preflight would 401.
        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        assertEquals("GET, PATCH, OPTIONS", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS])
        assertEquals(expectedAllowedHeaders, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS])
    }

    @Test
    fun `Rejected GET with Origin still carries the wildcard CORS header`() {
        // Unauthenticated, so the request itself fails; the browser must still be able to read the error.
        val response = exchange(HttpRequest.GET<Any>(claimsPath).header(HttpHeaders.ORIGIN, anyOrigin))

        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `Rejected GET with Origin exposes the challenge it is answered with`() {
        // An origin on localhost, because the framework refuses any other one to a server on localhost before
        // the request reaches the route.
        val response = exchange(HttpRequest.GET<Any>(claimsPath).header(HttpHeaders.ORIGIN, localOrigin))

        assertNotNull(response.headers[HttpHeaders.WWW_AUTHENTICATE])
        assertEquals(HttpHeaders.WWW_AUTHENTICATE, response.headers[HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS])
    }

    @Test
    fun `GET without Origin header does not add CORS headers`() {
        val response = exchange(HttpRequest.GET<Any>(claimsPath))

        assertNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    private fun exchange(request: HttpRequest<*>): HttpResponse<*> = try {
        httpClient.toBlocking().exchange(request, String::class.java)
    } catch (e: HttpClientResponseException) {
        e.response
    }
}
