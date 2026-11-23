package com.sympauthy.platform.queue.manager

import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.bean
import com.sympauthy.data.repository.QueueItemRepository
import com.sympauthy.data.withFixture
import com.sympauthy.platform.queue.mapper.QueueItemMapper
import com.sympauthy.platform.queue.model.QueueItem
import io.micronaut.transaction.annotation.Transactional
import io.mockk.every
import io.mockk.mockk
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Where an item is written, and when this instance is told to attempt it, against a real transaction of every
 * dialect: what is under test is when the database lets the queue know the change it reports has ended, which a
 * double would decide by itself.
 */
class QueueManagerTest {

    private val destination = "queue-manager-test"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `enqueue - Nudges once the transaction writing the item has committed`(database: Database) =
        withFixture(database) {
            val nudges = Nudges()
            val writer = database.bean<TransactionalQueueWriter>()

            val id = writer.writeThen(manager(nudges), destination, payload()) { nudges.bodyReturned = true }
            deleteOnEnd { repository<QueueItemRepository>().deleteById(id) }

            assertEquals(listOf(id to true), nudges.received, "The nudge came before the transaction ended.")
            assertNotNull(repository<QueueItemRepository>().findById(id))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `enqueue - Takes the item back with a transaction that rolls back`(database: Database) =
        withFixture(database) {
            val nudges = Nudges()
            val writer = database.bean<TransactionalQueueWriter>()

            assertThrows<IllegalStateException> {
                writer.writeThen(manager(nudges), destination, payload()) { error("the change failed") }
            }

            // The nudge comes all the same, and finds nothing to take.
            val (id, _) = nudges.received.single()
            assertNull(repository<QueueItemRepository>().findById(id))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `enqueue - Nudges at once an item written outside any transaction, due now and expiring after its age`(
        database: Database
    ) = withFixture(database) {
        val nudges = Nudges()
        val payload = payload()

        manager(nudges).enqueue(destination, "a_kind", payload, Duration.ofMinutes(10))

        val (id, _) = nudges.received.single()
        deleteOnEnd { repository<QueueItemRepository>().deleteById(id) }
        val stored = repository<QueueItemRepository>().findById(id)!!
        assertEquals(destination, stored.destination)
        assertEquals("a_kind", stored.kind)
        assertEquals(payload, stored.payload)
        assertEquals(stored.creationDate, stored.nextAttemptDate)
        assertEquals(stored.creationDate.plusMinutes(10), stored.expirationDate)
    }

    private fun payload() = """{"test":"${UUID.randomUUID()}"}"""

    private fun RepositoryFixture.manager(nudges: Nudges): QueueManager {
        val delivery = mockk<QueueDelivery>()
        every { delivery.nudge(match { it.destination == destination }) } answers {
            nudges.received.add(firstArg<QueueItem>().id to nudges.bodyReturned)
        }
        return QueueManager(
            queueItemRepository = repository(),
            queueItemMapper = QueueItemMapper(),
            queueDelivery = delivery
        )
    }

    /**
     * Every item nudged, beside whether the transaction's body had returned by then.
     */
    private class Nudges {
        val received = CopyOnWriteArrayList<Pair<UUID, Boolean>>()

        @Volatile
        var bodyReturned = false
    }
}

/**
 * A change writing an item and then going on, in one transaction.
 *
 * It is a bean rather than a method of the test because what is under test is the transaction the container
 * wraps the write in, and nothing a test body does opens one.
 */
@Singleton
open class TransactionalQueueWriter(
    private val queueItemRepository: QueueItemRepository
) {

    @Transactional
    open suspend fun writeThen(manager: QueueManager, destination: String, payload: String, rest: () -> Unit): UUID {
        manager.enqueue(destination, "a_kind", payload, maxAge = Duration.ofMinutes(10))
        val id = queueItemRepository.findAll().toList().single { it.payload == payload }.id!!
        rest()
        return id
    }
}
