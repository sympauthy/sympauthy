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

    // -- Discovery endpoints --

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
    }

    // -- OAuth2 endpoints --

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
    }

    private fun exchange(request: HttpRequest<*>): HttpResponse<*> = try {
        httpClient.toBlocking().exchange(request, String::class.java)
    } catch (e: HttpClientResponseException) {
        e.response
    }
}
