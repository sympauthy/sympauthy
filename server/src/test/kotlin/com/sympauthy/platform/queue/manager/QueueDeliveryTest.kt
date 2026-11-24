package com.sympauthy.platform.queue.manager

import com.sympauthy.config.ConfigReadiness
import com.sympauthy.config.model.QueueConfig
import com.sympauthy.config.model.advancedConfigOf
import com.sympauthy.config.model.shippedQueue
import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.data.model.QueueItemSummaryEntity
import com.sympauthy.data.repository.QueueItemRepository
import com.sympauthy.platform.lock.JobLeaseManager
import com.sympauthy.platform.monitoring.manager.ConditionMonitor
import com.sympauthy.platform.monitoring.model.Outcome
import com.sympauthy.platform.monitoring.telemetry.manager.InMemoryMetrics
import com.sympauthy.platform.monitoring.telemetry.manager.QueueMetrics
import com.sympauthy.platform.monitoring.telemetry.manager.QueueMetrics.Companion.QUEUE_GIVEN_UP
import com.sympauthy.platform.monitoring.telemetry.manager.QueueMetrics.Companion.QUEUE_OLDEST_AGE
import com.sympauthy.platform.monitoring.telemetry.manager.QueueMetrics.Companion.QUEUE_PENDING
import com.sympauthy.platform.monitoring.telemetry.manager.attributes
import com.sympauthy.platform.queue.mapper.QueueItemMapper
import com.sympauthy.platform.queue.model.DeliveryAttempt
import com.sympauthy.platform.queue.model.QueueItem
import com.sympauthy.platform.server.BackgroundScope
import io.micronaut.context.event.ShutdownEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class QueueDeliveryTest {

    @MockK
    lateinit var configReadiness: ConfigReadiness

    @MockK
    lateinit var queueItemRepository: QueueItemRepository

    private val holder = "queue-delivery-test-holder"

    private val jobLeaseManager = mockk<JobLeaseManager> { every { holder } returns this@QueueDeliveryTest.holder }

    private val metrics = InMemoryMetrics()

    private val now = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

    private val deliverer = AnsweringDeliverer()

    private fun delivery(
        vararg deliverers: QueueItemDeliverer = arrayOf(deliverer),
        queue: QueueConfig = shippedQueue()
    ) = QueueDelivery(
        deliverers = deliverers.toList(),
        configReadiness = configReadiness,
        uncheckedAdvancedConfig = advancedConfigOf(queue = queue),
        queueItemRepository = queueItemRepository,
        queueItemMapper = QueueItemMapper(),
        jobLeaseManager = jobLeaseManager,
        backgroundScope = BackgroundScope(),
        conditionMonitor = ConditionMonitor(),
        queueMetrics = QueueMetrics(metrics.openTelemetry)
    )

    /** What a retry, the expiry checked before an attempt, and the backlog's age read the time off. */
    private fun clockReads() {
        coEvery { queueItemRepository.now() } returns now
    }

    private fun countedGiveUp(outcome: String, errorType: String) = metrics.value(
        QUEUE_GIVEN_UP,
        attributes(
            "destination" to DESTINATION, "kind" to KIND, "outcome" to outcome, "error.type" to errorType
        )
    )

    @Test
    fun `attempt - Deletes the item its destination took`() = runTest {
        clockReads()
        val item = item()
        deliverer.answer = DeliveryAttempt.Delivered
        coEvery { queueItemRepository.deleteHeld(item.id, holder) } returns 1

        delivery().attempt(deliverer, item)

        assertEquals(listOf(item), deliverer.attempted)
        assertFalse(metrics.isRecorded(QUEUE_GIVEN_UP))
    }

    @Test
    fun `attempt - Retries an item after the delay its attempts so far call for`() = runTest {
        val item = item(attemptCount = 2)
        deliverer.answer = DeliveryAttempt.Retry
        clockReads()
        coEvery { queueItemRepository.reschedule(item.id, holder, 3, now.plusSeconds(40)) } returns 1

        delivery().attempt(deliverer, item)

        coVerify(exactly = 0) { queueItemRepository.deleteHeld(any(), any()) }
    }

    @Test
    fun `attempt - Gives up an item refused for good, counting nothing the deliverer did not`() = runTest {
        clockReads()
        val item = item()
        deliverer.answer = DeliveryAttempt.Refused
        coEvery { queueItemRepository.deleteHeld(item.id, holder) } returns 1

        delivery().attempt(deliverer, item)

        assertFalse(metrics.isRecorded(QUEUE_GIVEN_UP))
        coVerify(exactly = 0) { queueItemRepository.reschedule(any(), any(), any(), any()) }
    }

    @Test
    fun `attempt - Gives up an item that cannot be delivered, and counts it given up`() = runTest {
        clockReads()
        val item = item()
        deliverer.answer = DeliveryAttempt.Failed(Outcome.Error("broken"))
        coEvery { queueItemRepository.deleteHeld(item.id, holder) } returns 1

        delivery().attempt(deliverer, item)

        assertEquals(1, countedGiveUp("error", "broken"))
    }

    @Test
    fun `attempt - Counts no give-up of a kind its deliverer does not deliver`() = runTest {
        clockReads()
        val item = item(kind = "retired_kind")
        deliverer.answer = DeliveryAttempt.Failed(Outcome.Error("broken"))
        coEvery { queueItemRepository.deleteHeld(item.id, holder) } returns 1

        delivery().attempt(deliverer, item)

        assertFalse(metrics.isRecorded(QUEUE_GIVEN_UP))
    }

    @Test
    fun `attempt - Gives up, unattempted, an item that expired while it waited`() = runTest {
        val item = item(expirationDate = now.minusSeconds(1))
        clockReads()
        coEvery { queueItemRepository.deleteHeld(item.id, holder) } returns 1

        delivery().attempt(deliverer, item)

        assertTrue(deliverer.attempted.isEmpty(), "An expired item was handed to its deliverer.")
        assertEquals(1, countedGiveUp("error", QueueMetrics.EXPIRED))
    }

    @Test
    fun `attempt - Gives up an item whose next attempt would fall after its expiry`() = runTest {
        val item = item(expirationDate = now.plusSeconds(5))
        deliverer.answer = DeliveryAttempt.Retry
        clockReads()
        coEvery { queueItemRepository.deleteHeld(item.id, holder) } returns 1

        delivery().attempt(deliverer, item)

        assertEquals(1, countedGiveUp("error", QueueMetrics.EXPIRED))
        coVerify(exactly = 0) { queueItemRepository.reschedule(any(), any(), any(), any()) }
    }

    @Test
    fun `retryDelay - Doubles from the first delay up to the maximum`() {
        val delays = (1..8).map { QueueDelivery.retryDelay(it, shippedQueue()).seconds }

        assertEquals(listOf(10L, 20L, 40L, 80L, 120L, 120L, 120L, 120L), delays)
    }

    @Test
    fun `pollOnce - Claims its own destination's items, and publishes the backlog of every kind it delivers`() =
        runTest {
            coEvery { queueItemRepository.summarize() } returns listOf(
                QueueItemSummaryEntity(DESTINATION, KIND, 3, now.minusSeconds(90)),
                QueueItemSummaryEntity("another_destination", OTHER_KIND, 5, now)
            )
            clockReads()
            coEvery { queueItemRepository.claimDue(DESTINATION, now, 50) } returns emptyList()

            delivery().pollOnce(shippedQueue())

            assertEquals(3, metrics.value(QUEUE_PENDING, attributes("destination" to DESTINATION, "kind" to KIND)))
            assertEquals(
                90,
                metrics.value(QUEUE_OLDEST_AGE, attributes("destination" to DESTINATION, "kind" to KIND))
            )
            assertEquals(
                0,
                metrics.value(QUEUE_PENDING, attributes("destination" to DESTINATION, "kind" to OTHER_KIND))
            )
            assertFalse(
                metrics.points(QUEUE_PENDING).any { "another_destination" in it.asMap().values },
                "A destination no deliverer delivers was published."
            )
        }

    @Test
    fun `pollOnce - Hands each item it claims to its destination's deliverer`() = runTest {
        val entity = entity()
        coEvery { queueItemRepository.summarize() } returns emptyList()
        clockReads()
        coEvery { queueItemRepository.claimDue(DESTINATION, now, 50) } returns listOf(entity)
        coEvery { queueItemRepository.holdClaimed(listOf(entity.id!!), holder, now, any()) } returns 1
        coEvery { queueItemRepository.deleteHeld(entity.id!!, holder) } returns 1
        coEvery { queueItemRepository.release(holder) } returns 0
        val delivery = delivery()

        delivery.pollOnce(shippedQueue())
        delivery.onApplicationEvent(shutdown())

        assertEquals(listOf(entity.id), deliverer.attempted.map(QueueItem::id))
    }

    @Test
    fun `pollOnce - Hands back an item whose attempt failed on this server's side`() = runTest {
        val entity = entity()
        coEvery { queueItemRepository.summarize() } returns emptyList()
        clockReads()
        coEvery { queueItemRepository.claimDue(DESTINATION, now, 50) } returns listOf(entity)
        coEvery { queueItemRepository.holdClaimed(listOf(entity.id!!), holder, now, any()) } returns 1
        coEvery { queueItemRepository.deleteHeld(entity.id!!, holder) } throws IllegalStateException("unreachable")
        coEvery { queueItemRepository.releaseHeld(entity.id!!, holder) } returns 1
        coEvery { queueItemRepository.release(holder) } returns 0
        val delivery = delivery()

        delivery.pollOnce(shippedQueue())
        delivery.onApplicationEvent(shutdown())

        coVerify(exactly = 1) { queueItemRepository.releaseHeld(entity.id!!, holder) }
    }

    @Test
    fun `pollOnce - Gives up the expired items of a destination whose deliverer is disabled, handing it none`() =
        runTest {
            val disabled = AnsweringDeliverer(enabled = false)
            val entity = entity(expirationDate = now.minusSeconds(1))
            coEvery { queueItemRepository.summarize() } returns emptyList()
            clockReads()
            coEvery { queueItemRepository.claimExpired(DESTINATION, now, 50) } returns listOf(entity)
            coEvery { queueItemRepository.holdClaimed(listOf(entity.id!!), holder, now, any()) } returns 1
            coEvery { queueItemRepository.deleteHeld(entity.id!!, holder) } returns 1
            coEvery { queueItemRepository.release(holder) } returns 0
            val delivery = delivery(disabled)

            delivery.pollOnce(shippedQueue())
            delivery.onApplicationEvent(shutdown())

            assertTrue(disabled.attempted.isEmpty(), "An item was handed to a disabled deliverer.")
            assertEquals(1, countedGiveUp("error", QueueMetrics.EXPIRED))
            assertEquals(0, metrics.value(QUEUE_PENDING, attributes("destination" to DESTINATION, "kind" to KIND)))
            coVerify(exactly = 0) { queueItemRepository.claimDue(any(), any(), any()) }
        }

    @Test
    fun `pollOnce - Still claims when the backlog cannot be read`() = runTest {
        coEvery { queueItemRepository.summarize() } throws IllegalStateException("unreachable")
        clockReads()
        coEvery { queueItemRepository.claimDue(DESTINATION, now, 50) } returns emptyList()

        delivery().pollOnce(shippedQueue())
    }

    @Test
    fun `nudge - Leaves the item to the poll while the batch size is taken`() = runTest {
        val blocking = BlockingDeliverer()
        val first = item()
        val second = item()
        clockReads()
        coEvery { queueItemRepository.takeUnattempted(first.id, holder, any()) } returns 1
        coEvery { queueItemRepository.deleteHeld(first.id, holder) } returns 1
        coEvery { queueItemRepository.release(holder) } returns 0
        val delivery = delivery(blocking, queue = shippedQueue().copy(batchSize = 1))

        delivery.nudge(first)
        blocking.started.await()
        delivery.nudge(second)
        blocking.finish.complete(DeliveryAttempt.Delivered)
        delivery.onApplicationEvent(shutdown())

        coVerify(exactly = 0) { queueItemRepository.takeUnattempted(second.id, any(), any()) }
    }

    @Test
    fun `pollUntilStopped - Does not poll while the server reports itself unready`() = runTest {
        coEvery { configReadiness.getConfigurationErrors() } returns listOf(Exception("Invalid provider"))

        delivery().pollUntilStopped()

        coVerify(exactly = 0) { queueItemRepository.claimDue(any(), any(), any()) }
    }

    @Test
    fun `onApplicationEvent - Hands back every item this instance holds when it stops`() {
        coEvery { queueItemRepository.release(holder) } returns 2

        delivery().onApplicationEvent(shutdown())

        coVerify(exactly = 1) { queueItemRepository.release(holder) }
    }

    @Test
    fun `onApplicationEvent - Touches nothing when no destination is delivered to`() {
        delivery(*emptyArray()).onApplicationEvent(shutdown())

        coVerify(exactly = 0) { queueItemRepository.release(any()) }
    }

    @Test
    fun `renewLeases - Pushes back the lease of each item being attempted, and of no other`() = runTest {
        val blocking = BlockingDeliverer()
        val item = item()
        clockReads()
        coEvery { queueItemRepository.takeUnattempted(item.id, holder, any()) } returns 1
        coEvery {
            queueItemRepository.renew(listOf(item.id), holder, now, now.plus(Duration.ofMinutes(3)))
        } returns 1
        coEvery { queueItemRepository.deleteHeld(item.id, holder) } returns 1
        coEvery { queueItemRepository.release(holder) } returns 0
        val delivery = delivery(blocking)

        delivery.nudge(item)
        blocking.started.await()
        delivery.renewLeases()
        blocking.finish.complete(DeliveryAttempt.Delivered)
        delivery.onApplicationEvent(shutdown())
    }

    @Test
    fun `renewLeases - Renews nothing while nothing is attempted`() = runTest {
        delivery().renewLeases()

        coVerify(exactly = 0) { queueItemRepository.renew(any(), any(), any(), any()) }
    }

    private fun shutdown() = mockk<ShutdownEvent>()

    private fun item(
        kind: String = KIND,
        attemptCount: Int = 0,
        expirationDate: LocalDateTime = now.plusDays(1)
    ) = QueueItem(
        id = UUID.randomUUID(),
        destination = DESTINATION,
        kind = kind,
        payload = "{}",
        creationDate = now,
        expirationDate = expirationDate,
        attemptCount = attemptCount
    )

    private fun entity(expirationDate: LocalDateTime = now.plusDays(1)) = QueueItemEntity(
        destination = DESTINATION,
        kind = KIND,
        payload = "{}",
        creationDate = now,
        expirationDate = expirationDate
    ).apply { id = UUID.randomUUID() }

    /**
     * A deliverer answering every attempt with [answer], and keeping what it was handed.
     */
    private class AnsweringDeliverer(override val enabled: Boolean = true) : QueueItemDeliverer {
        override val destination = DESTINATION
        override val kinds = setOf(KIND, OTHER_KIND)

        var answer: DeliveryAttempt = DeliveryAttempt.Delivered
        val attempted = Collections.synchronizedList(mutableListOf<QueueItem>())

        override suspend fun attempt(item: QueueItem): DeliveryAttempt {
            attempted.add(item)
            return answer
        }
    }

    /**
     * A deliverer whose attempt says it [started] and then waits to be told how to [finish].
     */
    private class BlockingDeliverer : QueueItemDeliverer {
        override val destination = DESTINATION
        override val kinds = setOf(KIND)
        override val enabled = true

        val started = CompletableDeferred<QueueItem>()
        val finish = CompletableDeferred<DeliveryAttempt>()

        override suspend fun attempt(item: QueueItem): DeliveryAttempt {
            started.complete(item)
            return finish.await()
        }
    }

    private companion object {
        const val DESTINATION = "queue-delivery-test"
        const val KIND = "first_kind"
        const val OTHER_KIND = "second_kind"
    }
}
