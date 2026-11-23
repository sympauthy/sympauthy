package com.sympauthy.mail.manager

import com.sympauthy.mail.mapper.QueuedMailMapper
import com.sympauthy.mail.model.Mail
import com.sympauthy.mail.model.QueuedMail
import com.sympauthy.platform.monitoring.manager.ConditionMonitor
import com.sympauthy.platform.monitoring.manager.RemoteCallMonitor
import com.sympauthy.platform.monitoring.manager.RemoteCallMonitor.Companion.REMOTE_CALL_DURATION
import com.sympauthy.platform.monitoring.model.Condition
import com.sympauthy.platform.monitoring.model.Outcome
import com.sympauthy.platform.monitoring.telemetry.manager.InMemoryMetrics
import com.sympauthy.platform.monitoring.telemetry.manager.MailMetrics
import com.sympauthy.platform.monitoring.telemetry.manager.MailMetrics.Companion.MAIL_SENDS
import com.sympauthy.platform.monitoring.telemetry.manager.attributes
import com.sympauthy.platform.queue.model.DeliveryAttempt
import com.sympauthy.platform.queue.model.QueueItem
import io.micronaut.email.EmailException
import io.micronaut.serde.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import jakarta.mail.SendFailedException
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.test.runTest
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.FileNotFoundException
import java.net.ConnectException
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class MailDelivererTest {

    @MockK
    lateinit var mailSender: MailSender

    @MockK(relaxed = true)
    lateinit var mailBuilderFactory: TemplatedMailBuilderFactory

    private val queuedMailMapper = QueuedMailMapper(ObjectMapper.getDefault())

    private val conditionMonitor = ConditionMonitor()

    private val metrics = InMemoryMetrics()

    private fun deliverer() = MailDeliverer(
        mailSender = mailSender,
        mailBuilderFactory = mailBuilderFactory,
        queuedMailMapper = queuedMailMapper,
        conditionMonitor = conditionMonitor,
        remoteCallMonitor = RemoteCallMonitor(conditionMonitor, metrics.openTelemetry),
        mailMetrics = MailMetrics(metrics.openTelemetry)
    )

    private fun sendingFails(failure: Throwable) {
        coEvery { mailSender.send(any()) } throws failure
    }

    private fun recordedCall(outcome: String, errorType: String? = null) = metrics.count(
        REMOTE_CALL_DURATION,
        attributesOf("condition" to "mail", "operation" to "send", "outcome" to outcome, "error.type" to errorType)
    )

    private fun countedSend(outcome: String, errorType: String? = null) = metrics.value(
        MAIL_SENDS,
        attributesOf("mail" to "claim_validation", "outcome" to outcome, "error.type" to errorType)
    )

    @Test
    fun `attempt - Answers a mail the mail server took as delivered, and counts the send`() = runTest {
        coEvery { mailSender.send(any()) } returns Unit

        assertEquals(DeliveryAttempt.Delivered, deliverer().attempt(item()))

        assertEquals(1, recordedCall("success"))
        assertEquals(1, countedSend("success"))
    }

    @Test
    fun `attempt - Retries a mail the mail server could not be reached for, and degrades its condition`() = runTest {
        sendingFails(EmailException(MessagingException("Couldn't connect", ConnectException("Refused"))))

        assertEquals(DeliveryAttempt.Retry, deliverer().attempt(item()))

        assertTrue(conditionMonitor.isDegraded(Condition.Mail))
        assertEquals(1, recordedCall("error", "connection"))
        assertEquals(1, countedSend("error", "connection"))
    }

    @Test
    fun `attempt - Types a mail server refusing this server's credentials as authentication`() = runTest {
        sendingFails(EmailException(AuthenticationFailedException("535 Authentication failed")))

        assertEquals(DeliveryAttempt.Retry, deliverer().attempt(item()))

        assertTrue(conditionMonitor.isDegraded(Condition.Mail))
        assertEquals(1, recordedCall("error", "authentication"))
    }

    @Test
    fun `attempt - Retries greylisting without degrading the mail server's condition`() = runTest {
        sendingFails(EmailException(refusedWith(recipientRefused(451, "451 4.7.1 Greylisted, try again later"))))

        assertEquals(DeliveryAttempt.Retry, deliverer().attempt(item()))

        assertFalse(conditionMonitor.isDegraded(Condition.Mail))
        assertEquals(1, recordedCall("refused", "451"))
        assertEquals(1, countedSend("error", "451"))
    }

    @Test
    fun `attempt - Answers a recipient refused for good as refused, counting it on the send`() = runTest {
        sendingFails(EmailException(refusedWith(recipientRefused(550, "550 5.1.1 User unknown"))))

        assertEquals(DeliveryAttempt.Refused, deliverer().attempt(item()))

        assertEquals(1, countedSend("refused", "550"))
    }

    @Test
    fun `attempt - Retries a mail the mail server closed the connection on, and degrades its condition`() = runTest {
        sendingFails(EmailException(SMTPSendFailedException(".", -1, "[EOF]", null, null, null, null)))

        assertEquals(DeliveryAttempt.Retry, deliverer().attempt(item()))

        assertTrue(conditionMonitor.isDegraded(Condition.Mail))
        assertEquals(1, recordedCall("error", "connection_closed"))
    }

    @Test
    fun `attempt - Answers a full mailbox as refused whatever its reply code`() = runTest {
        sendingFails(EmailException(refusedWith(recipientRefused(452, "452 4.2.2 Mailbox full"))))

        assertEquals(DeliveryAttempt.Refused, deliverer().attempt(item()))

        assertEquals(1, countedSend("refused", "452"))
    }

    @Test
    fun `attempt - Answers a mail that failed in no listed way as failed, counting no send`() = runTest {
        sendingFails(EmailException(FileNotFoundException("validation_code.ftlh")))

        assertEquals(
            DeliveryAttempt.Failed(Outcome.Error(FileNotFoundException::class.java.name)),
            deliverer().attempt(item())
        )

        assertFalse(conditionMonitor.isDegraded(Condition.Mail))
        assertFalse(metrics.isRecorded(REMOTE_CALL_DURATION))
        assertFalse(metrics.isRecorded(MAIL_SENDS), "a mail that never reached the mail server is no send")
    }

    @Test
    fun `attempt - Answers an item of a kind this build sends no mail of as failed, without sending it`() = runTest {
        val attempt = deliverer().attempt(item(kind = "retired_mail"))

        assertTrue(attempt is DeliveryAttempt.Failed, "An item no mail can be drawn from was answered $attempt.")
        coVerify(exactly = 0) { mailSender.send(any()) }
        assertFalse(metrics.isRecorded(MAIL_SENDS))
    }

    private fun recipientRefused(code: Int, response: String) = SMTPAddressFailedException(
        InternetAddress("person@example.com"), "RCPT TO:<person@example.com>", code, response
    )

    private fun refusedWith(refusal: SMTPAddressFailedException) =
        SendFailedException("Invalid Addresses", refusal)

    private fun attributesOf(vararg pairs: Pair<String, String?>) =
        attributes(*pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toTypedArray())

    private fun item(kind: String = Mail.CLAIM_VALIDATION.kind) = QueueItem(
        id = UUID.randomUUID(),
        destination = MailDeliverer.DESTINATION,
        kind = kind,
        payload = queuedMailMapper.toPayload(
            QueuedMail(Mail.CLAIM_VALIDATION, "person@example.com", Locale.US, emptyMap())
        ),
        creationDate = LocalDateTime.of(2026, 1, 1, 12, 0, 0),
        expirationDate = LocalDateTime.of(2026, 1, 1, 12, 10, 0),
        attemptCount = 0
    )
}
