package com.sympauthy.client.authorization.webhook

import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookRequest
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookResult
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.AuthorizationWebhookAdvancedConfig
import io.micronaut.http.client.HttpClient
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@MicronautTest(
    environments = ["default", "test"],
    startApplication = false
)
class AuthorizationWebhookClientTest {

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var advancedConfig: AdvancedConfig

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: AuthorizationWebhookClient

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val httpClient = HttpClient.create(mockWebServer.url("/").toUrl())
        client = AuthorizationWebhookClient(httpClient, objectMapper, advancedConfig)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private val request = AuthorizationWebhookRequest(
        userId = "user-1",
        clientId = "client-1",
        requestedScopes = listOf("scope1"),
        claims = emptyMap()
    )

    private fun mockAuthorizationWebhook(): AuthorizationWebhook {
        return AuthorizationWebhook(
            url = URI.create(mockWebServer.url("/webhook").toString()),
            secret = "test-secret",
            onFailure = AuthorizationWebhookOnFailure.DENY_ALL,
        )
    }

    @Test
    fun `callWebhook - returns Success with granted scopes on valid response`() = runBlocking<Unit> {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"scopes":{"scope1":"grant"}}""")
        )

        val result = client.callWebhook(mockAuthorizationWebhook(), request)

        assertIs<AuthorizationWebhookResult.Success>(result)
        assertEquals(mapOf("scope1" to "grant"), result.response.scopes)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(
            recordedRequest.getHeader(AuthorizationWebhookClient.SIGNATURE_HEADER)!!
                .startsWith(AuthorizationWebhookClient.SIGNATURE_PREFIX)
        )
        assertEquals("application/json", recordedRequest.getHeader("Content-Type"))

        // Verify the body is valid JSON with expected fields
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"user_id\""))
        assertTrue(body.contains("\"client_id\""))
        assertTrue(body.contains("\"requested_scopes\""))
    }

    @Test
    fun `callWebhook - returns Failure on timeout`() = runBlocking<Unit> {
        val shortTimeoutConfig = AuthorizationWebhookAdvancedConfig(timeout = Duration.ofMillis(100))
        val timeoutAdvancedConfig = io.mockk.mockk<com.sympauthy.config.model.EnabledAdvancedConfig>()
        io.mockk.every { timeoutAdvancedConfig.authorizationWebhook } returns shortTimeoutConfig
        val httpClient = HttpClient.create(mockWebServer.url("/").toUrl())
        val timeoutClient = AuthorizationWebhookClient(httpClient, objectMapper, timeoutAdvancedConfig)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"scopes":{"scope1":"grant"}}""")
                .setBodyDelay(5, TimeUnit.SECONDS)
        )

        val result = timeoutClient.callWebhook(mockAuthorizationWebhook(), request)

        assertIs<AuthorizationWebhookResult.Failure>(result)
    }

    @Test
    fun `callWebhook - returns Failure on client error response`() = runBlocking<Unit> {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"bad request"}""")
        )

        val result = client.callWebhook(mockAuthorizationWebhook(), request)

        assertIs<AuthorizationWebhookResult.Failure>(result)
    }

    @Test
    fun `callWebhook - returns Failure on invalid response payload`() = runBlocking<Unit> {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""not valid json""")
        )

        val result = client.callWebhook(mockAuthorizationWebhook(), request)

        assertIs<AuthorizationWebhookResult.Failure>(result)
    }

    @Test
    fun `computeHmacSha256 - produces correct signature for known input`() {
        val signature = client.computeHmacSha256("secret", "message")

        assertEquals(
            "8b5f48702995c1598c573db1e21866a9b825d4a794d169d7060a03605796360b",
            signature
        )
    }

    @Test
    fun `computeHmacSha256 - different secrets produce different signatures`() {
        val sig1 = client.computeHmacSha256("secret1", "body")
        val sig2 = client.computeHmacSha256("secret2", "body")

        assert(sig1 != sig2) { "Different secrets should produce different signatures" }
    }

    @Test
    fun `computeHmacSha256 - different bodies produce different signatures`() {
        val sig1 = client.computeHmacSha256("secret", "body1")
        val sig2 = client.computeHmacSha256("secret", "body2")

        assert(sig1 != sig2) { "Different bodies should produce different signatures" }
    }
}
