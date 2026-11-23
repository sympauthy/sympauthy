package com.sympauthy.mail

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.config.ConfigReadiness
import com.sympauthy.config.model.advancedConfigOf
import com.sympauthy.config.model.shippedQueue
import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.data.model.QueueItemSummaryEntity
import com.sympauthy.data.repository.QueueItemRepository
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.RemoteCallMonitor
import com.sympauthy.monitoring.RemoteCallMonitor.Companion.REMOTE_CALL_DURATION
import com.sympauthy.monitoring.telemetry.InMemoryMetrics
import com.sympauthy.monitoring.telemetry.MailMetrics
import com.sympauthy.monitoring.telemetry.MailMetrics.Companion.MAIL_SENDS
import com.sympauthy.monitoring.telemetry.QueueMetrics
import com.sympauthy.monitoring.telemetry.QueueMetrics.Companion.QUEUE_GIVEN_UP
import com.sympauthy.monitoring.telemetry.QueueMetrics.Companion.QUEUE_OLDEST_AGE
import com.sympauthy.monitoring.telemetry.QueueMetrics.Companion.QUEUE_PENDING
import com.sympauthy.monitoring.telemetry.attributes
import com.sympauthy.server.BackgroundScope
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.email.EmailException
import io.micronaut.serde.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import jakarta.mail.SendFailedException
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.test.runTest
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.FileNotFoundException
import java.net.ConnectException
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class MailDeliveryTest {

    @MockK
    lateinit var mailSender: MailSender

    @MockK
    lateinit var configReadiness: ConfigReadiness

    @MockK(relaxed = true)
    lateinit var mailBuilderFactory: TemplatedMailBuilderFactory

    @MockK
    lateinit var queueItemRepository: QueueItemRepository

    private val objectMapper = ObjectMapper.getDefault()

    private val holder = "mail-delivery-test-holder"

    private val jobLeaseManager = mockk<JobLeaseManager> { every { holder } returns this@MailDeliveryTest.holder }

    private val conditionMonitor = ConditionMonitor()

    private val metrics = InMemoryMetrics()

    private val now = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

    private fun delivery(sender: MailSender? = mailSender) = MailDelivery(
        mailSender = sender,
        configReadiness = configReadiness,
        uncheckedAdvancedConfig = advancedConfigOf(),
        mailBuilderFactory = mailBuilderFactory,
        queueItemRepository = queueItemRepository,
        objectMapper = objectMapper,
        jobLeaseManager = jobLeaseManager,
        backgroundScope = BackgroundScope(),
        conditionMonitor = conditionMonitor,
        remoteCallMonitor = RemoteCallMonitor(conditionMonitor, metrics.openTelemetry),
        mailMetrics = MailMetrics(metrics.openTelemetry),
        queueMetrics = QueueMetrics(metrics.openTelemetry)
    )

    private fun sendingFails(failure: Throwable) {
        coEvery { mailSender.send(any()) } throws failure
    }

    /** What a retry reads its next attempt off. */
    private fun clockReads() {
        coEvery { queueItemRepository.now() } returns now
    }

    private fun recordedCall(outcome: String, errorType: String? = null) = metrics.count(
        REMOTE_CALL_DURATION,
        attributesOf("condition" to "mail", "operation" to "send", "outcome" to outcome, "error.type" to errorType)
    )

    private fun countedSend(outcome: String, errorType: String? = null) = metrics.value(
        MAIL_SENDS,
        attributesOf("mail" to "claim_validation", "outcome" to outcome, "error.type" to errorType)
    )

    private fun countedGiveUp(errorType: String) = metrics.value(
        QUEUE_GIVEN_UP,
        attributes(
            "destination" to "mail", "kind" to "claim_validation", "outcome" to "error", "error.type" to errorType
        )
    )

    @Test
    fun `attempt - Deletes the mail it delivered and counts the send`() = runTest {
        val mail = mail()
        coEvery { mailSender.send(any()) } returns Unit
        coEvery { queueItemRepository.deleteHeld(mail.id!!, holder) } returns 1

        delivery().attempt(mail)

        assertEquals(1, recordedCall("success"))
        assertEquals(1, countedSend("success"))
    }

    @Test
    fun `attempt - Retries a mail the mail server could not be reached for, and degrades its condition`() = runTest {
        val mail = mail()
        sendingFails(EmailException(MessagingException("Couldn't connect", ConnectException("Refused"))))
        clockReads()
        coEvery { queueItemRepository.reschedule(mail.id!!, holder, 1, now.plusSeconds(10)) } returns 1

        delivery().attempt(mail)

        assertTrue(conditionMonitor.isDegraded(Condition.Mail))
        assertEquals(1, recordedCall("error", "connection"))
        assertEquals(1, countedSend("error", "connection"))
    }

    @Test
    fun `attempt - Types a mail server refusing this server's credentials as authentication`() = runTest {
        val mail = mail()
        sendingFails(EmailException(AuthenticationFailedException("535 Authentication failed")))
        clockReads()
        coEvery { queueItemRepository.reschedule(mail.id!!, holder, 1, any()) } returns 1

        delivery().attempt(mail)

        assertTrue(conditionMonitor.isDegraded(Condition.Mail))
        assertEquals(1, recordedCall("error", "authentication"))
    }

    @Test
    fun `attempt - Retries greylisting without degrading the mail server's condition`() = runTest {
        val mail = mail(attemptCount = 2)
        sendingFails(EmailException(refusedWith(recipientRefused(451, "451 4.7.1 Greylisted, try again later"))))
        clockReads()
        coEvery { queueItemRepository.reschedule(mail.id!!, holder, 3, now.plusSeconds(40)) } returns 1

        delivery().attempt(mail)

        assertFalse(conditionMonitor.isDegraded(Condition.Mail))
        assertEquals(1, recordedCall("refused", "451"))
        assertEquals(1, countedSend("error", "451"))
    }

    @Test
    fun `attempt - Gives up a recipient refused for good, counting it on the send alone`() = runTest {
        val mail = mail()
        sendingFails(EmailException(refusedWith(recipientRefused(550, "550 5.1.1 User unknown"))))
        coEvery { queueItemRepository.deleteHeld(mail.id!!, holder) } returns 1

        delivery().attempt(mail)

        assertEquals(1, countedSend("refused", "550"))
        assertFalse(metrics.isRecorded(QUEUE_GIVEN_UP))
        coVerify(exactly = 0) { queueItemRepository.reschedule(any(), any(), any(), any()) }
    }

    @Test
    fun `attempt - Gives up a full mailbox whatever its reply code`() = runTest {
        val mail = mail()
        sendingFails(EmailException(refusedWith(recipientRefused(452, "452 4.2.2 Mailbox full"))))
        coEvery { queueItemRepository.deleteHeld(mail.id!!, holder) } returns 1

        delivery().attempt(mail)

        assertEquals(1, countedSend("refused", "452"))
        coVerify(exactly = 0) { queueItemRepository.reschedule(any(), any(), any(), any()) }
    }

    @Test
    fun `attempt - Gives up a mail that failed in no listed way, and counts it given up`() = runTest {
        val mail = mail()
        sendingFails(EmailException(FileNotFoundException("validation_code.ftlh")))
        coEvery { queueItemRepository.deleteHeld(mail.id!!, holder) } returns 1

        delivery().attempt(mail)

        assertFalse(conditionMonitor.isDegraded(Condition.Mail))
        assertFalse(metrics.isRecorded(REMOTE_CALL_DURATION))
        assertFalse(metrics.isRecorded(MAIL_SENDS), "a mail that never reached the mail server is no send")
        assertEquals(1, countedGiveUp(EmailException::class.java.name))
    }

    @Test
    fun `attempt - Gives up, unattempted, a mail that expired while it waited`() = runTest {
        val mail = mail(expirationDate = now.minusSeconds(1))
        clockReads()
        coEvery { queueItemRepository.deleteHeld(mail.id!!, holder) } returns 1

        delivery().attempt(mail)

        coVerify(exactly = 0) { mailSender.send(any()) }
        assertEquals(1, countedGiveUp(QueueMetrics.EXPIRED))
    }

    @Test
    fun `attempt - Gives up a mail whose next attempt would fall after its expiry`() = runTest {
        val mail = mail(expirationDate = now.plusSeconds(5))
        sendingFails(EmailException(MessagingException("Couldn't connect", ConnectException("Refused"))))
        clockReads()
        coEvery { queueItemRepository.deleteHeld(mail.id!!, holder) } returns 1

        delivery().attempt(mail)

        assertEquals(1, countedGiveUp(QueueMetrics.EXPIRED))
        coVerify(exactly = 0) { queueItemRepository.reschedule(any(), any(), any(), any()) }
    }

    @Test
    fun `attempt - Gives up an item of a kind this build sends no mail of, without sending it`() = runTest {
        val mail = mail(kind = "retired_mail")
        coEvery { queueItemRepository.deleteHeld(mail.id!!, holder) } returns 1

        delivery().attempt(mail)

        coVerify(exactly = 0) { mailSender.send(any()) }
        assertFalse(metrics.isRecorded(MAIL_SENDS))
        assertFalse(metrics.isRecorded(QUEUE_GIVEN_UP))
    }

    @Test
    fun `retryDelay - Doubles from the first delay up to the maximum`() {
        val delays = (1..8).map { MailDelivery.retryDelay(it, shippedQueue()).seconds }

        assertEquals(listOf(10L, 20L, 40L, 80L, 120L, 120L, 120L, 120L), delays)
    }

    @Test
    fun `pollOnce - Claims no more than the batch size leaves, and publishes every kind's backlog`() = runTest {
        coEvery { queueItemRepository.summarize() } returns listOf(
            QueueItemSummaryEntity(
                MailDelivery.DESTINATION, Mail.CLAIM_VALIDATION.metricName, 3, LocalDateTime.now().minusSeconds(90)
            ),
            QueueItemSummaryEntity("another_destination", Mail.PASSWORD_CHANGED.metricName, 5, LocalDateTime.now())
        )
        clockReads()
        coEvery { queueItemRepository.claimDue(MailDelivery.DESTINATION, now, 50) } returns emptyList()

        delivery().pollOnce(shippedQueue())

        assertEquals(3, metrics.value(QUEUE_PENDING, attributes("destination" to "mail", "kind" to "claim_validation")))
        assertTrue(
            metrics.value(QUEUE_OLDEST_AGE, attributes("destination" to "mail", "kind" to "claim_validation")) >= 90
        )
        assertEquals(0, metrics.value(QUEUE_PENDING, attributes("destination" to "mail", "kind" to "password_changed")))
        assertEquals(
            0,
            metrics.value(QUEUE_OLDEST_AGE, attributes("destination" to "mail", "kind" to "password_changed"))
        )
    }

    @Test
    fun `pollUntilStopped - Does not poll while the server reports itself unready`() = runTest {
        coEvery { configReadiness.getConfigurationErrors() } returns listOf(Exception("Invalid provider"))

        delivery().pollUntilStopped()

        coVerify(exactly = 0) { queueItemRepository.claimDue(any(), any(), any()) }
    }

    @Test
    fun `onApplicationEvent - Hands back every mail this instance holds when it stops`() {
        coEvery { queueItemRepository.release(holder) } returns 2

        delivery().onApplicationEvent(mockk<ShutdownEvent>())

        coVerify(exactly = 1) { queueItemRepository.release(holder) }
    }

    @Test
    fun `onApplicationEvent - Touches nothing when no mail server is configured`() {
        delivery(sender = null).onApplicationEvent(mockk<ShutdownEvent>())

        coVerify(exactly = 0) { queueItemRepository.release(any()) }
    }

    @Test
    fun `renewLeases - Pushes back the leases this instance holds`() = runTest {
        clockReads()
        coEvery { queueItemRepository.renew(holder, now, now.plus(Duration.ofMinutes(3))) } returns 1

        delivery().renewLeases()

        coVerify(exactly = 1) { queueItemRepository.renew(holder, now, now.plus(Duration.ofMinutes(3))) }
    }

    private fun recipientRefused(code: Int, response: String) = SMTPAddressFailedException(
        InternetAddress("person@example.com"), "RCPT TO:<person@example.com>", code, response
    )

    private fun refusedWith(refusal: SMTPAddressFailedException) =
        SendFailedException("Invalid Addresses", refusal)

    private fun attributesOf(vararg pairs: Pair<String, String?>) =
        attributes(*pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toTypedArray())

    private fun mail(
        kind: String = Mail.CLAIM_VALIDATION.metricName,
        attemptCount: Int = 0,
        expirationDate: LocalDateTime? = null
    ) = QueueItemEntity(
        destination = MailDelivery.DESTINATION,
        kind = kind,
        payload = objectMapper.writeValueAsString(
            MailPayload(receiver = "person@example.com", locale = "en", parameters = emptyMap())
        ),
        creationDate = now,
        expirationDate = expirationDate,
        attemptCount = attemptCount,
        holder = holder,
        leaseExpirationDate = now.plusMinutes(3)
    ).apply { id = UUID.randomUUID() }
}
