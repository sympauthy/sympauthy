package com.sympauthy.business.manager.mail

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.business.manager.lock.LeasedJob.MAIL_BACKLOG
import com.sympauthy.config.ConfigReadiness
import com.sympauthy.data.model.MailQueueEntity
import com.sympauthy.data.repository.MailQueueRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class MailQueueTest {

    @MockK
    lateinit var mailSender: MailSender

    @MockK
    lateinit var configReadiness: ConfigReadiness

    @MockK(relaxed = true)
    lateinit var mailBuilderFactory: TemplatedMailBuilderFactory

    @MockK
    lateinit var mailQueueRepository: MailQueueRepository

    @MockK
    lateinit var jobLeaseManager: JobLeaseManager

    private val first = mail("first@example.com")
    private val second = mail("second@example.com")

    /** The subject is built per test: whether a sender is configured is what enables the queue. */
    private fun queue(sender: MailSender? = mailSender) = MailQueue(
        mailSender = sender,
        configReadiness = configReadiness,
        mailBuilderFactory = mailBuilderFactory,
        mailQueueRepository = mailQueueRepository,
        jobLeaseManager = jobLeaseManager
    )

    private fun holdingTheLease() {
        coEvery { jobLeaseManager.withLease(MAIL_BACKLOG, any()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
            true
        }
    }

    private fun expectNoReplay() {
        coVerify(exactly = 0) {
            mailQueueRepository.deleteByExpirationDateBefore(any())
            mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(any())
        }
    }

    @Test
    fun `replayBacklog - Replays the queue when the configuration has no error`() = runTest {
        holdingTheLease()
        coEvery { configReadiness.getConfigurationErrors() } returns emptyList()
        coEvery { mailQueueRepository.deleteByExpirationDateBefore(any()) } returns 0
        coEvery { mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(any()) } returns emptyList()

        queue().replayBacklog()

        coVerify {
            mailQueueRepository.deleteByExpirationDateBefore(any())
            mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(any())
        }
    }

    @Test
    fun `replayBacklog - Replays nothing when the configuration has errors`() = runTest {
        coEvery { configReadiness.getConfigurationErrors() } returns listOf(Exception("Invalid provider"))

        queue().replayBacklog()

        coVerify(exactly = 0) { jobLeaseManager.withLease(any(), any()) }
        expectNoReplay()
    }

    @Test
    fun `replayBacklog - Reads no configuration when mail sending is not configured`() = runTest {
        queue(sender = null).replayBacklog()

        coVerify(exactly = 0) { configReadiness.getConfigurationErrors() }
        expectNoReplay()
    }

    @Test
    fun `replayBacklog - Replays nothing when another instance holds the lease`() = runTest {
        coEvery { configReadiness.getConfigurationErrors() } returns emptyList()
        coEvery { jobLeaseManager.withLease(MAIL_BACKLOG, any()) } returns false

        queue().replayBacklog()

        expectNoReplay()
    }

    @Test
    fun `replayUnsentMails - Sends every unsent mail and deletes the row it was read from`() = runTest {
        coEvery { mailQueueRepository.deleteByExpirationDateBefore(any()) } returns 0
        coEvery { mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(any()) } returns
            listOf(first, second)
        coEvery { mailQueueRepository.deleteById(any()) } returns 1
        coEvery { mailSender.send(any()) } returns Unit

        queue().replayUnsentMails()

        coVerify(exactly = 2) { mailSender.send(any()) }
        coVerify(exactly = 1) { mailQueueRepository.deleteById(first.id!!) }
        coVerify(exactly = 1) { mailQueueRepository.deleteById(second.id!!) }
    }

    @Test
    fun `replayUnsentMails - Discards the mails that expired unsent before delivering the rest`() = runTest {
        coEvery { mailQueueRepository.deleteByExpirationDateBefore(any()) } returns 3
        coEvery { mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(any()) } returns
            emptyList()

        queue().replayUnsentMails()

        coVerify(exactly = 0) { mailSender.send(any()) }
    }

    private fun mail(receiver: String) = MailQueueEntity(
        template = "validation-code",
        locale = "en",
        receiver = receiver,
        subjectKey = "subject",
        parameters = emptyMap(),
        creationDate = LocalDateTime.of(2026, 1, 1, 12, 0, 0),
        expirationDate = null
    ).apply { id = UUID.randomUUID() }
}
