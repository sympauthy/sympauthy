package com.sympauthy.business.manager.mail

import com.sympauthy.config.ConfigReadiness
import com.sympauthy.data.repository.MailQueueRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class MailQueueTest {

    @MockK
    lateinit var mailSender: MailSender

    @MockK
    lateinit var configReadiness: ConfigReadiness

    @MockK
    lateinit var mailBuilderFactory: TemplatedMailBuilderFactory

    @MockK
    lateinit var mailQueueRepository: MailQueueRepository

    /** The subject is built per test: whether a sender is configured is what enables the queue. */
    private fun queue(sender: MailSender? = mailSender) = MailQueue(
        mailSender = sender,
        configReadiness = configReadiness,
        mailBuilderFactory = mailBuilderFactory,
        mailQueueRepository = mailQueueRepository
    )

    private fun expectNoReplay() {
        coVerify(exactly = 0) {
            mailQueueRepository.deleteByExpirationDateBefore(any())
            mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(any())
        }
    }

    @Test
    fun `onApplicationEvent - Replays the queue when the configuration has no error`() {
        coEvery { configReadiness.getConfigurationErrors() } returns emptyList()
        coEvery { mailQueueRepository.deleteByExpirationDateBefore(any()) } returns 0
        coEvery { mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(any()) } returns emptyList()

        queue().onApplicationEvent(mockk())

        coVerify {
            mailQueueRepository.deleteByExpirationDateBefore(any())
            mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(any())
        }
    }

    @Test
    fun `onApplicationEvent - Replays nothing when the configuration has errors`() {
        coEvery { configReadiness.getConfigurationErrors() } returns listOf(Exception("Invalid provider"))

        queue().onApplicationEvent(mockk())

        expectNoReplay()
    }

    @Test
    fun `onApplicationEvent - Reads no configuration when mail sending is not configured`() {
        queue(sender = null).onApplicationEvent(mockk())

        coVerify(exactly = 0) { configReadiness.getConfigurationErrors() }
        expectNoReplay()
    }
}
