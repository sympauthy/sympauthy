package com.sympauthy.api.filter

import com.sympauthy.api.AbstractFlowIntegrationTest
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WildcardCorsFilterTest : AbstractFlowIntegrationTest() {

    private val anyOrigin = "http://some-app.example.com"
    private val discoveryPath = "/.well-known/openid-configuration"
    private val jwksPath = "/.well-known/public.jwk"
    private val tokenPath = "/api/oauth2/token"
    private val revokePath = "/api/oauth2/revoke"
    private val userInfoPath = "/api/openid/userinfo"

    /** Mandatory headers, then cors.allowed-headers as declared in application-default.yml. */
    private val expectedAllowedHeaders = "Content-Type, Authorization, DPoP, X-Requested-With"

    @Test
    fun `discovery - OPTIONS preflight returns 200 with wildcard CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(discoveryPath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        assertNotNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS])
        assertNotNull(response.headers[HttpHeaders.ACCESS_CONTROL_MAX_AGE])
        assertEquals(expectedAllowedHeaders, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS])
    }

    @Test
    fun `discovery - GET with Origin header returns wildcard CORS header`() {
        val response = exchange(
            HttpRequest.GET<Any>(discoveryPath).header(HttpHeaders.ORIGIN, anyOrigin)
        )

        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `discovery - GET without Origin header does not add CORS headers`() {
        val response = exchange(HttpRequest.GET<Any>(discoveryPath))

        assertNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `discovery - JWKS OPTIONS preflight returns wildcard CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(jwksPath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        // DPoP is advertised on every tier, not only on the token endpoint.
        assertEquals(expectedAllowedHeaders, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS])
    }

    @Test
    fun `oauth2 - OPTIONS preflight on token endpoint returns wildcard CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(tokenPath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        assertNotNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS])
        assertNotNull(response.headers[HttpHeaders.ACCESS_CONTROL_MAX_AGE])
        assertEquals(expectedAllowedHeaders, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS])
    }

    @Test
    fun `oauth2 - POST with Origin header returns wildcard CORS header`() {
        val response = exchange(
            HttpRequest.POST(tokenPath, "grant_type=client_credentials")
                .header(HttpHeaders.ORIGIN, anyOrigin)
                .contentType("application/x-www-form-urlencoded")
        )

        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `oauth2 - POST without Origin header does not add CORS headers`() {
        val response = exchange(
            HttpRequest.POST(tokenPath, "grant_type=client_credentials")
                .contentType("application/x-www-form-urlencoded")
        )

        assertNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `oauth2 - OPTIONS preflight on revoke endpoint returns wildcard CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(revokePath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        // DPoP is advertised on every tier, not only on the token endpoint.
        assertEquals(expectedAllowedHeaders, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS])
    }

    @Test
    fun `openid - OPTIONS preflight on userinfo returns wildcard CORS headers`() {
        val request = HttpRequest.OPTIONS<Any>(userInfoPath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)

        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
        assertEquals(expectedAllowedHeaders, response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS])
    }

    @Test
    fun `openid - preflight is answered without a token, before the security filter`() {
        val request = HttpRequest.OPTIONS<Any>(userInfoPath)
            .header(HttpHeaders.ORIGIN, anyOrigin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)

        // No Authorization header: userinfo is @Secured(IS_USER), so an unfiltered preflight would 401.
        val response = httpClient.toBlocking().exchange(request, String::class.java)

        assertEquals(200, response.status.code)
    }

    @Test
    fun `openid - rejected GET with Origin still carries the wildcard CORS header`() {
        // Unauthenticated, so the request itself fails; the browser must still be able to read the error.
        val response = exchange(HttpRequest.GET<Any>(userInfoPath).header(HttpHeaders.ORIGIN, anyOrigin))

        assertEquals("*", response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    @Test
    fun `openid - GET without Origin header does not add CORS headers`() {
        val response = exchange(HttpRequest.GET<Any>(userInfoPath))

        assertNull(response.headers[HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN])
    }

    private fun exchange(request: HttpRequest<*>): HttpResponse<*> = try {
        httpClient.toBlocking().exchange(request, String::class.java)
    } catch (e: HttpClientResponseException) {
        e.response
    }
}
