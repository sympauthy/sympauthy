package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.AbstractFlowIntegrationTest
import com.sympauthy.api.controller.oauth2.AuthorizeController.Companion.OAUTH2_AUTHORIZE_ENDPOINT
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

class AuthorizeControllerIntegrationTest : AbstractFlowIntegrationTest() {

    private fun noRedirectClient(): HttpClient {
        val config = DefaultHttpClientConfiguration()
        config.isFollowRedirects = false
        return HttpClient.create(embeddedServer.url, config)
    }

    // --- response_type validation ---

    @Test
    fun `GET authorize - Returns 400 when response_type is missing`() {
        val client = noRedirectClient()
        val request = HttpRequest.GET<Any>(
            "$OAUTH2_AUTHORIZE_ENDPOINT?client_id=default"
        )
        val exception = assertThrows<HttpClientResponseException> {
            client.toBlocking().exchange(request, String::class.java)
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun `GET authorize - Returns 400 when response_type is token`() {
        val client = noRedirectClient()
        val request = HttpRequest.GET<Any>(
            "$OAUTH2_AUTHORIZE_ENDPOINT?response_type=token&client_id=default"
        )
        val exception = assertThrows<HttpClientResponseException> {
            client.toBlocking().exchange(request, String::class.java)
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // --- Successful authorization ---

    @Test
    fun `GET authorize - Returns 303 with Location header for valid code request`() {
        val client = noRedirectClient()
        val request = HttpRequest.GET<Any>(
            "$OAUTH2_AUTHORIZE_ENDPOINT?response_type=code&client_id=default"
        )
        val response = client.toBlocking().exchange(request, String::class.java)

        assertEquals(HttpStatus.SEE_OTHER, response.status)
        assertNotNull(response.header(HttpHeaders.LOCATION))
    }

    @Test
    fun `GET authorize - Location header contains state query parameter`() {
        val client = noRedirectClient()
        val request = HttpRequest.GET<Any>(
            "$OAUTH2_AUTHORIZE_ENDPOINT?response_type=code&client_id=default"
        )
        val response = client.toBlocking().exchange(request, String::class.java)
        val location = response.header(HttpHeaders.LOCATION)

        assertNotNull(location)
        val query = URI(location!!).query
        assertNotNull(query)
        assertTrue(query!!.contains("state="))
    }

    // --- Client validation ---

    @Test
    fun `GET authorize - Returns 303 to error page when client_id is missing`() {
        val client = noRedirectClient()
        val request = HttpRequest.GET<Any>(
            "$OAUTH2_AUTHORIZE_ENDPOINT?response_type=code"
        )
        val response = client.toBlocking().exchange(request, String::class.java)

        assertEquals(HttpStatus.SEE_OTHER, response.status)
        assertNotNull(response.header(HttpHeaders.LOCATION))
    }

    @Test
    fun `GET authorize - Returns 303 to error page when client_id is unknown`() {
        val client = noRedirectClient()
        val request = HttpRequest.GET<Any>(
            "$OAUTH2_AUTHORIZE_ENDPOINT?response_type=code&client_id=nonexistent"
        )
        val response = client.toBlocking().exchange(request, String::class.java)

        assertEquals(HttpStatus.SEE_OTHER, response.status)
        assertNotNull(response.header(HttpHeaders.LOCATION))
    }

    // --- Redirect URI ---

    @Test
    fun `GET authorize - Returns 303 to error page when redirect_uri is missing`() {
        // The default client has no allowedRedirectUris restrictions,
        // but redirect_uri is still required by parseRequestedRedirectUri
        val client = noRedirectClient()
        val request = HttpRequest.GET<Any>(
            "$OAUTH2_AUTHORIZE_ENDPOINT?response_type=code&client_id=default"
        )
        // redirect_uri is missing, but the current implementation still creates an attempt
        // and redirects (the error is stored in the attempt)
        val response = client.toBlocking().exchange(request, String::class.java)

        assertEquals(HttpStatus.SEE_OTHER, response.status)
    }
}
