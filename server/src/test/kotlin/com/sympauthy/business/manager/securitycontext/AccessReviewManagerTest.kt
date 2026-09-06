package com.sympauthy.business.manager.securitycontext

import com.sympauthy.business.model.client.AccessReviewOnFailure
import com.sympauthy.business.model.client.AccessReviewTrigger
import com.sympauthy.business.model.client.AccessReviewWebhook
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.business.model.securitycontext.AccessReviewReason
import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.business.model.securitycontext.SecurityContext
import com.sympauthy.business.model.securitycontext.SecurityContextGeo
import com.sympauthy.client.accessreview.webhook.AccessReviewWebhookClient
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookRequest
import com.sympauthy.client.accessreview.webhook.model.AccessReviewWebhookResult
import com.sympauthy.config.model.AccessReviewWebhookAdvancedConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class AccessReviewManagerTest {

    @MockK
    lateinit var advancedConfig: EnabledAdvancedConfig

    @MockK
    lateinit var securityContextManager: SecurityContextManager

    @MockK
    lateinit var accessReviewWebhookClient: AccessReviewWebhookClient

    @InjectMockKs
    lateinit var accessReviewManager: AccessReviewManager

    private val userId = UUID.randomUUID()

    private val observedRequest = ObservedRequest(peer = "198.51.100.10", headers = emptyMap())

    private val client = mockk<Client>()

    @Test
    fun `reviewAccess - Allow, and record nothing, where the client configured no webhook`() = runTest {
        every { client.accessReviewWebhook } returns null

        val decision = accessReviewManager.reviewAccess(
            client = client,
            userId = userId,
            reason = AccessReviewReason.USERINFO,
            observedRequest = observedRequest
        )

        assertEquals(AccessReviewDecision.ALLOW, decision)
        coVerify(exactly = 0) { securityContextManager.recordObservation(any<ObservedRequest>(), any(), any()) }
        coVerify(exactly = 0) { accessReviewWebhookClient.callWebhook(any(), any()) }
    }

    @Test
    fun `reviewAccess - Record the place, then ask the client about it`() = runTest {
        val current = context()
        stubReview(current, result = AccessReviewWebhookResult.Success(AccessReviewDecision.ALLOW))

        val decision = review()

        assertEquals(AccessReviewDecision.ALLOW, decision)
        coVerifyOrder {
            securityContextManager.recordObservation(observedRequest, emptyList(), userId)
            accessReviewWebhookClient.callWebhook(any(), any())
            securityContextManager.markReviewed(current, AccessReviewDecision.ALLOW)
        }
    }

    @Test
    fun `reviewAccess - Ask again about a place the last review refused`() = runTest {
        val current = context(lastDecision = AccessReviewDecision.DENY)
        stubReview(current, result = AccessReviewWebhookResult.Success(AccessReviewDecision.DENY))

        val decision = review()

        assertEquals(AccessReviewDecision.DENY, decision)
        coVerify(exactly = 1) { accessReviewWebhookClient.callWebhook(any(), any()) }
    }

    @Test
    fun `reviewAccess - Serve a place already allowed without asking`() = runTest {
        val current = context(lastDecision = AccessReviewDecision.ALLOW)
        coEvery {
            securityContextManager.recordObservation(observedRequest, emptyList(), userId)
        } returns current

        val decision = review()

        assertEquals(AccessReviewDecision.ALLOW, decision)
        coVerify(exactly = 0) { accessReviewWebhookClient.callWebhook(any(), any()) }
        coVerify(exactly = 0) { securityContextManager.markReviewed(any(), any()) }
    }

    @Test
    fun `reviewAccess - Ask on every validation where the client asked for that`() = runTest {
        val current = context(lastDecision = AccessReviewDecision.ALLOW)
        stubReview(current, result = AccessReviewWebhookResult.Success(AccessReviewDecision.ALLOW))

        val decision = review(webhook(on = AccessReviewTrigger.EVERY_VALIDATION))

        assertEquals(AccessReviewDecision.ALLOW, decision)
        coVerify(exactly = 1) { accessReviewWebhookClient.callWebhook(any(), any()) }
    }

    @Test
    fun `reviewAccess - Refuse where the webhook did not answer and the client said to`() = runTest {
        stubReview(context(), result = AccessReviewWebhookResult.Failure("timed out"))

        val decision = review()

        assertEquals(AccessReviewDecision.DENY, decision)
        coVerify(exactly = 0) { securityContextManager.markReviewed(any(), any()) }
    }

    @Test
    fun `reviewAccess - Allow where the webhook did not answer and the client said to`() = runTest {
        stubReview(context(), result = AccessReviewWebhookResult.Failure("timed out"))

        val decision = review(webhook(onFailure = AccessReviewOnFailure.ALLOW))

        assertEquals(AccessReviewDecision.ALLOW, decision)
        coVerify(exactly = 0) { securityContextManager.markReviewed(any(), any()) }
    }

    @Test
    fun `reviewAccess - Hand the client the place it is asked about and the ones before it`() = runTest {
        val current = context()
        val past = context(ip = "203.0.113.9")
        val sent = slot<AccessReviewWebhookRequest>()
        every { client.id } returns "my-app"
        every { advancedConfig.accessReviewWebhook } returns AccessReviewWebhookAdvancedConfig(
            timeout = Duration.ofSeconds(2),
            pastContexts = 10
        )
        coEvery {
            securityContextManager.recordObservation(observedRequest, emptyList(), userId)
        } returns current
        coEvery { securityContextManager.listPastContexts(userId, 10, current.id) } returns listOf(past)
        coEvery { securityContextManager.markReviewed(current, AccessReviewDecision.ALLOW) } returns current
        coEvery { accessReviewWebhookClient.callWebhook(any(), capture(sent)) } returns
            AccessReviewWebhookResult.Success(AccessReviewDecision.ALLOW)

        review()

        assertEquals(userId.toString(), sent.captured.userId)
        assertEquals("my-app", sent.captured.clientId)
        assertEquals("userinfo", sent.captured.reason)
        assertEquals("198.51.100.10", sent.captured.current.ip)
        assertEquals(listOf("203.0.113.9"), sent.captured.past.map { it.ip })
    }

    private suspend fun review(webhook: AccessReviewWebhook = webhook()): AccessReviewDecision {
        every { client.accessReviewWebhook } returns webhook
        return accessReviewManager.reviewAccess(
            client = client,
            userId = userId,
            reason = AccessReviewReason.USERINFO,
            observedRequest = observedRequest
        )
    }

    /**
     * Stub the recording of [current] and the answer the webhook gives about it, plus the bound the
     * past it is shown is read under. The client's own id is read only where the webhook is called.
     */
    private fun stubReview(
        current: SecurityContext,
        past: List<SecurityContext> = emptyList(),
        result: AccessReviewWebhookResult
    ) {
        every { client.id } returns "my-app"
        every { advancedConfig.accessReviewWebhook } returns AccessReviewWebhookAdvancedConfig(
            timeout = Duration.ofSeconds(2),
            pastContexts = 10
        )
        coEvery {
            securityContextManager.recordObservation(observedRequest, emptyList(), userId)
        } returns current
        coEvery { securityContextManager.listPastContexts(userId, 10, current.id) } returns past
        coEvery { accessReviewWebhookClient.callWebhook(any(), any()) } returns result
        if (result is AccessReviewWebhookResult.Success) {
            coEvery { securityContextManager.markReviewed(current, result.decision) } returns current
        }
    }

    private fun webhook(
        on: AccessReviewTrigger = AccessReviewTrigger.NEW_CONTEXT,
        onFailure: AccessReviewOnFailure = AccessReviewOnFailure.DENY
    ) = AccessReviewWebhook(
        url = URI.create("https://my-app.example/security/access-review"),
        secret = "a-shared-secret",
        on = on,
        onFailure = onFailure
    )

    private fun context(
        ip: String = "198.51.100.10",
        lastDecision: AccessReviewDecision? = null
    ) = SecurityContext(
        id = UUID.randomUUID(),
        userId = userId,
        fingerprint = "fingerprint",
        ip = ip,
        userAgent = "Mozilla/5.0",
        geo = SecurityContextGeo("FR", "OCC", "Toulouse"),
        firstSeenDate = LocalDateTime.of(2026, 1, 1, 12, 0),
        lastSeenDate = LocalDateTime.of(2026, 1, 1, 12, 0),
        observationCount = 1,
        expirationDate = LocalDateTime.of(2026, 7, 1, 12, 0),
        lastDecision = lastDecision,
        lastDecisionDate = lastDecision?.let { LocalDateTime.of(2026, 1, 1, 12, 0) }
    )
}
