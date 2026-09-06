package com.sympauthy.client.accessreview.webhook

import com.sympauthy.business.model.client.AccessReviewOnFailure
import com.sympauthy.business.model.client.AccessReviewTrigger
import com.sympauthy.business.model.client.AccessReviewWebhook
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookContext
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookRequest
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookResult
import com.sympauthy.config.model.AdvancedConfig
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@MicronautTest(
    environments = ["default", "test", "h2"],
    startApplication = false
)
class AccessReviewWebhookClientTest {

    @Inject
    lateinit var objectMapper: ObjectMapper

    @Inject
    lateinit var advancedConfig: AdvancedConfig

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: AccessReviewWebhookClient

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = AccessReviewWebhookClient(
            HttpClient.create(mockWebServer.url("/").toUrl()),
            objectMapper,
            advancedConfig
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `callWebhook - Answer the decision the webhook named`() = runBlocking<Unit> {
        enqueue("""{"decision":"revoke_session"}""")

        val result = client.callWebhook(webhook(), request())

        assertIs<AccessReviewWebhookResult.Success>(result)
        assertEquals(AccessReviewDecision.REVOKE_SESSION, result.decision)
    }

    @Test
    fun `callWebhook - Fail on an answer naming no decision`() = runBlocking<Unit> {
        enqueue("""{"decision":"yes"}""")

        val result = client.callWebhook(webhook(), request())

        assertIs<AccessReviewWebhookResult.Failure>(result)
    }

    @Test
    fun `callWebhook - Fail on a webhook that refused the call`() = runBlocking<Unit> {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = client.callWebhook(webhook(), request())

        assertIs<AccessReviewWebhookResult.Failure>(result)
    }

    @Test
    fun `callWebhook - Fail on a webhook that did not answer in time`() = runBlocking<Unit> {
        enqueue("""{"decision":"allow"}""", delaySeconds = 5)

        val result = client.callWebhook(webhook(), request())

        assertIs<AccessReviewWebhookResult.Failure>(result)
    }

    @Test
    fun `callWebhook - Sign the body it sent with the secret the client holds`() = runBlocking<Unit> {
        enqueue("""{"decision":"allow"}""")

        client.callWebhook(webhook(), request())

        val recorded = mockWebServer.takeRequest()
        val body = recorded.body.readUtf8()
        assertEquals(
            "sha256=${client.computeHmacSha256("a-shared-secret", body)}",
            recorded.getHeader(AccessReviewWebhookClient.SIGNATURE_HEADER)
        )
        assertTrue(body.contains("\"user_id\""), "The body does not carry the user it is about.")
    }

    private fun enqueue(body: String, delaySeconds: Long = 0) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
                .apply { if (delaySeconds > 0) setBodyDelay(delaySeconds, TimeUnit.SECONDS) }
        )
    }

    private fun webhook() = AccessReviewWebhook(
        url = URI.create(mockWebServer.url("/access-review").toString()),
        secret = "a-shared-secret",
        on = AccessReviewTrigger.NEW_CONTEXT,
        onFailure = AccessReviewOnFailure.DENY
    )

    private fun request() = AccessReviewWebhookRequest(
        userId = "a5f2c0d6-0000-4000-8000-000000000000",
        clientId = "my-app",
        reason = "userinfo",
        current = context(),
        past = listOf(context())
    )

    private fun context() = AccessReviewWebhookContext(
        ip = "198.51.100.10",
        userAgent = "Mozilla/5.0",
        country = "FR",
        region = "OCC",
        city = "Toulouse",
        firstSeenDate = "2026-01-01T12:00:00Z",
        lastSeenDate = "2026-01-01T12:00:00Z",
        observationCount = 1,
        new = true
    )
}
