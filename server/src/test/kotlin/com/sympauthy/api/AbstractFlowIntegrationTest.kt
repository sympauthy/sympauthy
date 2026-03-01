package com.sympauthy.api

import com.sympauthy.api.controller.oauth2.AuthorizeController.Companion.OAUTH2_AUTHORIZE_ENDPOINT
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import java.net.URI

/**
 * Base class for integration tests that exercise the flow API (`/api/v1/flow/`).
 *
 * Extend this class instead of `AbstractIntegrationTest` whenever a test needs to
 * interact with the authorization flow, because it provides:
 *
 * - A pre-configured [httpClient] bound to the root of the embedded server.
 * - [createAuthorizeAttempt]: starts an authorization code flow with the test client (`default`)
 *   and returns the opaque `state` token that the flow API requires on every subsequent request.
 *   Call this in a `@BeforeEach` method and pass the state to the endpoints under test.
 */
@MicronautTest(
    environments = ["default", "test"],
    startApplication = true
)
abstract class AbstractFlowIntegrationTest {

    @Inject
    lateinit var embeddedServer: EmbeddedServer

    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

    fun createAuthorizeAttempt(): String {
        val config = DefaultHttpClientConfiguration()
        config.isFollowRedirects = false
        val client = HttpClient.create(embeddedServer.url, config)
        val request = HttpRequest.GET<Any>(
            "$OAUTH2_AUTHORIZE_ENDPOINT?response_type=code&client_id=default"
        )
        val location = client.toBlocking()
            .exchange(request, String::class.java)
            .header(HttpHeaders.LOCATION)
            ?: error("Missing Location header in authorize response")
        return URI(location).query.split("&")
            .first { it.startsWith("state=") }
            .removePrefix("state=")
    }
}
