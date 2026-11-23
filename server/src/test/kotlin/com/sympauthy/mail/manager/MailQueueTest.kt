package com.sympauthy.mail.manager

import com.sympauthy.mail.mapper.QueuedMailMapper
import com.sympauthy.mail.model.Mail
import com.sympauthy.mail.model.QueuedMail
import com.sympauthy.queue.manager.QueueManager
import io.micronaut.serde.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.*

@ExtendWith(MockKExtension::class)
class MailQueueTest {

    @MockK
    lateinit var queueManager: QueueManager

    @MockK
    lateinit var mailDeliverer: MailDeliverer

    private val queuedMailMapper = QueuedMailMapper(ObjectMapper.getDefault())

    @Test
    fun `send - Queues nothing when no mail server is configured`() = runTest {
        every { mailDeliverer.enabled } returns false

        MailQueue(mailDeliverer = mailDeliverer, queueManager = queueManager, queuedMailMapper = queuedMailMapper)
            .send(Mail.CLAIM_VALIDATION, Locale.US, "person@example.com", emptyMap(), Duration.ofMinutes(10))

        coVerify(exactly = 0) { queueManager.enqueue(any(), any(), any(), any()) }
    }

    @Test
    fun `send - Queues the mail as an item of its kind for the mail server`() = runTest {
        val payload = queuedMailMapper.toPayload(
            QueuedMail(Mail.PASSWORD_RESET_CODE, "person@example.com", Locale.US, mapOf("code" to "123456"))
        )
        coEvery {
            queueManager.enqueue(MailDeliverer.DESTINATION, "password_reset_code", payload, Duration.ofMinutes(10))
        } returns Unit

        every { mailDeliverer.enabled } returns true

        MailQueue(mailDeliverer = mailDeliverer, queueManager = queueManager, queuedMailMapper = queuedMailMapper)
            .send(
                Mail.PASSWORD_RESET_CODE,
                Locale.US,
                "person@example.com",
                mapOf("code" to "123456"),
                Duration.ofMinutes(10)
            )
    }
}
