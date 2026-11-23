package com.sympauthy.mail

import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.bean
import com.sympauthy.data.repository.QueueItemRepository
import com.sympauthy.data.withFixture
import io.micronaut.serde.ObjectMapper
import io.micronaut.transaction.annotation.Transactional
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Where a mail is written, and when this instance is told to attempt it, against a real transaction of every
 * dialect: what is under test is when the database lets the queue know the change it reports has ended, which a
 * double would decide by itself.
 */
class MailQueueTest {

    private val receiver = "mail-queue-test@example.org"

    @Test
    fun `send - Writes nothing when no mail server is configured`() = runTest {
        val repository = mockk<QueueItemRepository>()
        val delivery = mockk<MailDelivery>()

        MailQueue(mailSender = null, queueItemRepository = repository, objectMapper = mockk(), mailDelivery = delivery)
            .send(Mail.CLAIM_VALIDATION, Locale.US, receiver, emptyMap())

        verify(exactly = 0) { delivery.nudge(any()) }
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `send - Nudges once the transaction writing the mail has committed`(database: Database) =
        withFixture(database) {
            val nudges = Nudges()
            val writer = database.bean<TransactionalMailWriter>()

            val id = writer.writeThen(queue(nudges), receiver) { nudges.bodyReturned = true }
            deleteOnEnd { repository<QueueItemRepository>().deleteById(id) }

            assertEquals(listOf(id to true), nudges.received, "The nudge came before the transaction ended.")
            assertNotNull(repository<QueueItemRepository>().findById(id))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `send - Takes the mail back with a transaction that rolls back`(database: Database) =
        withFixture(database) {
            val nudges = Nudges()
            val writer = database.bean<TransactionalMailWriter>()

            assertThrows<IllegalStateException> {
                writer.writeThen(queue(nudges), receiver) { error("the change failed") }
            }

            // The nudge comes all the same, and finds nothing to take.
            val (id, _) = nudges.received.single()
            assertNull(repository<QueueItemRepository>().findById(id))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `send - Nudges at once a mail written outside any transaction`(database: Database) =
        withFixture(database) {
            val nudges = Nudges()

            queue(nudges).send(Mail.CLAIM_VALIDATION, Locale.US, receiver, emptyMap())

            val (id, _) = nudges.received.single()
            deleteOnEnd { repository<QueueItemRepository>().deleteById(id) }
            assertTrue(repository<QueueItemRepository>().existsById(id))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `send - Writes the mail as an item of its kind, its payload holding what it was sent for`(
        database: Database
    ) = withFixture(database) {
        val nudges = Nudges()

        queue(nudges).send(Mail.PASSWORD_RESET_CODE, Locale.US, receiver, mapOf("code" to "123456"))

        val (id, _) = nudges.received.single()
        deleteOnEnd { repository<QueueItemRepository>().deleteById(id) }
        val stored = repository<QueueItemRepository>().findById(id)!!
        assertEquals(MailDelivery.DESTINATION, stored.destination)
        assertEquals(Mail.PASSWORD_RESET_CODE, Mail.ofKindOrNull(stored.kind))
        assertEquals(
            MailPayload(receiver = receiver, locale = "en-US", parameters = mapOf("code" to "123456")),
            database.bean<ObjectMapper>().readValue(stored.payload, MailPayload::class.java)
        )
    }

    private fun RepositoryFixture.queue(nudges: Nudges): MailQueue {
        val delivery = mockk<MailDelivery>()
        every { delivery.nudge(any()) } answers { nudges.received.add(firstArg<UUID>() to nudges.bodyReturned) }
        return MailQueue(
            mailSender = mockk(),
            queueItemRepository = repository(),
            objectMapper = database.bean(),
            mailDelivery = delivery
        )
    }

    /**
     * Every mail nudged, beside whether the transaction's body had returned by then.
     */
    private class Nudges {
        val received = CopyOnWriteArrayList<Pair<UUID, Boolean>>()

        @Volatile
        var bodyReturned = false
    }
}

/**
 * A change writing a mail and then going on, in one transaction.
 *
 * It is a bean rather than a method of the test because what is under test is the transaction the container
 * wraps the write in, and nothing a test body does opens one.
 */
@Singleton
open class TransactionalMailWriter(
    private val queueItemRepository: QueueItemRepository
) {

    @Transactional
    open suspend fun writeThen(queue: MailQueue, receiver: String, rest: () -> Unit): UUID {
        queue.send(Mail.CLAIM_VALIDATION, Locale.US, receiver, emptyMap())
        val id = queueItemRepository.findAll().toList().single { receiver in it.payload }.id!!
        rest()
        return id
    }
}
