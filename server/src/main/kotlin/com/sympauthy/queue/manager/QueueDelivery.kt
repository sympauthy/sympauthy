package com.sympauthy.queue.manager

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.business.manager.lock.JobLeaseManager.Companion.GRACE_PERIOD
import com.sympauthy.config.ConfigReadiness
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.QueueConfig
import com.sympauthy.data.repository.QueueItemRepository
import com.sympauthy.monitoring.loggerForClass
import com.sympauthy.monitoring.manager.ConditionMonitor
import com.sympauthy.monitoring.model.Outcome
import com.sympauthy.monitoring.telemetry.manager.QueueMetrics
import com.sympauthy.monitoring.telemetry.manager.QueueMetrics.Companion.EXPIRED
import com.sympauthy.queue.mapper.QueueItemMapper
import com.sympauthy.queue.model.DeliveryAttempt
import com.sympauthy.queue.model.QueueItem
import com.sympauthy.server.BackgroundScope
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.core.order.Ordered
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Delivers the items [QueueManager] wrote, at least once, handing each to its destination's [QueueItemDeliverer],
 * retrying it until it goes through or expires, and sharing the work with every other instance of the deployment.
 * What it promises, and why, is `docs/design/delivery.md`.
 *
 * **An item is attempted under a lease on its row**, held by [JobLeaseManager.holder] and renewed with the job
 * leases for as long as its attempt runs, so no other instance attempts it meanwhile and one that died leaves it to
 * be taken once the lease lapses. Between two attempts nobody holds it.
 *
 * **An item is attempted as soon as its commit returns**, on the [nudge] the queue gives, and otherwise by the poll
 * every instance runs every `advanced.queue.poll-interval`: what an instance left when it died, a retry whose time
 * has come, a nudge that never came. A nudge's attempt never waits behind the poll's.
 *
 * **A destination whose deliverer is disabled delivers nothing, and its items are given up as they expire**, by the
 * same poll, so that what was queued before it stopped delivering neither waits for good nor goes out long after.
 *
 * Every coroutine it starts reports its own failure: they are launched in [BackgroundScope], which installs no
 * handler.
 */
@Singleton
open class QueueDelivery(
    @Inject private val deliverers: List<QueueItemDeliverer>,
    @Inject private val configReadiness: ConfigReadiness,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig,
    @Inject private val queueItemRepository: QueueItemRepository,
    @Inject private val queueItemMapper: QueueItemMapper,
    @Inject private val jobLeaseManager: JobLeaseManager,
    @Inject private val backgroundScope: BackgroundScope,
    @Inject private val conditionMonitor: ConditionMonitor,
    @Inject private val queueMetrics: QueueMetrics
) : ApplicationEventListener<ShutdownEvent>, Ordered {

    private val logger = loggerForClass()

    /**
     * The attempts under way, a scope of their own beneath [BackgroundScope] so that stopping the poll leaves them
     * running and a shutdown can wait for them alone.
     */
    private val attempts = CoroutineScope(
        backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job)
    )

    /**
     * How many items of each destination this instance is attempting, nudged or polled, which
     * `advanced.queue.batch-size` bounds per destination.
     */
    private val held: Map<String, AtomicInteger> = deliverers.associate { it.destination to AtomicInteger() }

    /**
     * The items this instance is attempting, whose leases alone [renewLeases] pushes back: a lease lasts as long as
     * its attempt, and one this instance failed to hand back lapses rather than being renewed for good.
     */
    private val attempting: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var poll: Job? = null

    @Volatile
    private var stopping = false

    private val holder: String
        get() = jobLeaseManager.holder

    /**
     * How many items of one destination this instance attempts at once: `advanced.queue.batch-size`, or one
     * where the configuration is not usable and this instance attempts only what it was nudged for.
     */
    private val batchSize: Int
        get() = (uncheckedAdvancedConfig as? EnabledAdvancedConfig)?.queue?.batchSize ?: 1

    /**
     * Start the poll on this instance once the server is ready, unless no destination is delivered to or the server
     * reports itself unready.
     *
     * Configuration is read once, so an unready server stays unready until it restarts, and the poll is not
     * started at all: the items waiting stay where they are, since one sent from a server answering errors points
     * its reader at it. That is said once, as `docs/standards/config-layer-code-standard.md` has work that obeys
     * readiness say it.
     */
    @EventListener
    fun startPolling(@Suppress("UNUSED_PARAMETER") event: ServiceReadyEvent) {
        if (deliverers.isEmpty()) return
        poll = backgroundScope.launch { pollUntilStopped() }
    }

    /**
     * Poll every `advanced.queue.poll-interval` until cancelled, or not at all where the server reports itself
     * unready.
     */
    internal suspend fun pollUntilStopped() {
        if (configReadiness.getConfigurationErrors().isNotEmpty()) {
            logger.warn {
                "Queue: not polling — the configuration has errors and this server reports itself " +
                    "unready. Waiting items stay in the queue until a server that is ready delivers them."
            }
            return
        }
        val config = (uncheckedAdvancedConfig as EnabledAdvancedConfig).queue
        while (currentCoroutineContext().isActive) {
            pollOnce(config)
            delay(config.pollInterval.toMillis())
        }
    }

    /**
     * Attempt [item] now, on this instance, once the commit that wrote it has returned.
     *
     * It takes the item only where nobody has, and never attempted it: an item whose transaction rolled back is
     * found by nothing, and one another instance's poll took first is that instance's. Where this instance already
     * attempts as many items of its destination as `advanced.queue.batch-size` allows, it leaves the item to the
     * next poll.
     *
     * It is not `suspend`: a connection's synchronization calls it, from no coroutine.
     */
    fun nudge(item: QueueItem) {
        if (stopping) return
        val deliverer = deliverers.firstOrNull { it.enabled && it.destination == item.destination } ?: return
        val count = held.getValue(deliverer.destination)
        if (!count.tryIncrementBelow(batchSize)) return
        attempts.launch {
            try {
                reportingFailure("attempt item ${item.id}") {
                    val now = queueItemRepository.now()
                    if (queueItemRepository.takeUnattempted(item.id, holder, now.plus(GRACE_PERIOD)) == 1) {
                        attemptHeld(deliverer, item)
                    }
                }
            } finally {
                count.decrementAndGet()
            }
        }
    }

    /**
     * Publish the backlog, then claim for each destination as many items as this instance's attempts leave room
     * for, and attempt each: those that are due where its deliverer is enabled, and only those that expired where it
     * is not. The backlog is read apart, so that a gauge failing to read delays no item.
     */
    internal suspend fun pollOnce(config: QueueConfig) {
        reportingFailure("read the queue's backlog") {
            queueMetrics.recordBacklog(readBacklog())
        }
        for (deliverer in deliverers) {
            if (stopping) return
            reportingFailure("poll the queue for ${deliverer.destination}") {
                claimFor(deliverer, config)
            }
        }
    }

    private suspend fun claimFor(deliverer: QueueItemDeliverer, config: QueueConfig) {
        val count = held.getValue(deliverer.destination)
        val free = config.batchSize - count.get()
        if (free <= 0) return
        claim(deliverer, free).forEach { item ->
            count.incrementAndGet()
            attempts.launch {
                try {
                    attemptHeld(deliverer, item)
                } finally {
                    count.decrementAndGet()
                }
            }
        }
    }

    /**
     * Claim up to [limit] items for the destination of [deliverer] that are held by nobody, or by an instance whose
     * lease lapsed, and hold them for this instance: items that are due where [deliverer] is enabled, and items that
     * expired, for it to give up, where it is not.
     *
     * The claim and the hold are one transaction: outside one, the rows the claim locks are released by the
     * autocommit that took them, and an instance polling at the same moment claims the same items.
     */
    @Transactional
    internal open suspend fun claim(deliverer: QueueItemDeliverer, limit: Int): List<QueueItem> {
        val now = queueItemRepository.now()
        val claimed = if (deliverer.enabled) {
            queueItemRepository.claimDue(deliverer.destination, now, limit)
        } else {
            queueItemRepository.claimExpired(deliverer.destination, now, limit)
        }.map(queueItemMapper::toQueueItem)
        if (claimed.isEmpty()) return claimed
        queueItemRepository.holdClaimed(
            ids = claimed.map(QueueItem::id),
            holder = holder,
            now = now,
            expiresAt = now.plus(GRACE_PERIOD)
        )
        return claimed
    }

    /**
     * Push back the lease on every item this instance is attempting, as [JobLeaseManager.renewLeases] does a job's.
     */
    suspend fun renewLeases() {
        val ids = attempting.toList()
        if (ids.isEmpty()) return
        reportingFailure("renew the queue's leases") {
            val now = queueItemRepository.now()
            queueItemRepository.renew(ids, holder, now, now.plus(GRACE_PERIOD))
        }
    }

    /**
     * Attempt [item], which this instance holds, and hand it back where the attempt fails for a reason of this
     * server's own — the database not answering it — so that the next poll takes it again rather than the lease
     * holding it until it lapses. A hand-back that fails too leaves the lease to lapse, since only an item being
     * attempted is renewed.
     */
    private suspend fun attemptHeld(deliverer: QueueItemDeliverer, item: QueueItem) {
        attempting.add(item.id)
        try {
            attempt(deliverer, item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report(failure, "attempt item ${item.id}")
            withContext(NonCancellable) {
                reportingFailure("hand back item ${item.id}") {
                    queueItemRepository.releaseHeld(item.id, holder)
                }
            }
        } finally {
            attempting.remove(item.id)
        }
    }

    /**
     * Hand [item], which this instance holds, to [deliverer], and leave it delivered, waiting for a retry, or given
     * up. An item past its expiry is given up without being handed.
     */
    internal suspend fun attempt(deliverer: QueueItemDeliverer, item: QueueItem) {
        if (item.expirationDate.isBefore(queueItemRepository.now())) {
            giveUpExpired(deliverer, item)
            return
        }
        when (val attempt = deliverer.attempt(item)) {
            DeliveryAttempt.Delivered, DeliveryAttempt.Refused -> queueItemRepository.deleteHeld(item.id, holder)
            DeliveryAttempt.Retry -> retry(deliverer, item)
            is DeliveryAttempt.Failed -> {
                recordGivenUp(deliverer, item, attempt.outcome)
                queueItemRepository.deleteHeld(item.id, holder)
            }
        }
    }

    /**
     * Leave [item] for its next attempt, or give it up where that attempt would fall after its expiry.
     *
     * Where the configuration is not usable, the item is handed back due at once, for an instance that is ready:
     * this one does not poll.
     */
    private suspend fun retry(deliverer: QueueItemDeliverer, item: QueueItem) {
        val attemptCount = item.attemptCount + 1
        val now = queueItemRepository.now()
        val config = (uncheckedAdvancedConfig as? EnabledAdvancedConfig)?.queue
        val nextAttemptDate = config?.let { now.plus(retryDelay(attemptCount, it)) } ?: now
        if (nextAttemptDate.isAfter(item.expirationDate)) {
            giveUpExpired(deliverer, item)
            return
        }
        queueItemRepository.reschedule(item.id, holder, attemptCount, nextAttemptDate)
    }

    private suspend fun giveUpExpired(deliverer: QueueItemDeliverer, item: QueueItem) {
        logger.debug {
            "Gave up item ${item.id} (destination=${item.destination}, kind=${item.kind}): it expired before it " +
                "went through."
        }
        recordGivenUp(deliverer, item, Outcome.Error(EXPIRED))
        queueItemRepository.deleteHeld(item.id, holder)
    }

    private fun recordGivenUp(deliverer: QueueItemDeliverer, item: QueueItem, outcome: Outcome) {
        if (item.kind in deliverer.kinds) {
            queueMetrics.recordGivenUp(deliverer.destination, item.kind, outcome)
        }
    }

    /**
     * How many items wait, and for how long the oldest has, for every kind each deliverer delivers, enabled or not.
     */
    private suspend fun readBacklog(): List<QueueMetrics.Backlog> {
        val now = queueItemRepository.now()
        val summaries = queueItemRepository.summarize().associateBy { it.destination to it.kind }
        return deliverers.flatMap { deliverer ->
            deliverer.kinds.map { kind ->
                val summary = summaries[deliverer.destination to kind]
                QueueMetrics.Backlog(
                    destination = deliverer.destination,
                    kind = kind,
                    pending = summary?.pendingCount ?: 0,
                    oldestAgeSeconds = summary?.let { Duration.between(it.oldestCreationDate, now).seconds } ?: 0
                )
            }
        }
    }

    /**
     * Ahead of [BackgroundScope], which cancels everything this started: the attempts under way are let finish
     * first.
     *
     * Declared through the interface rather than `@Order`, since the publisher reads the order off the listener
     * instance.
     */
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    /**
     * Stop taking items, let the attempts under way finish for `advanced.queue.shutdown-grace`, then hand back every
     * item this instance still holds, so another instance takes it on its next poll rather than once the lease
     * lapses. An attempt still running then may deliver its item after another instance has: the most that is lost
     * is a second copy.
     */
    override fun onApplicationEvent(event: ShutdownEvent) {
        if (deliverers.isEmpty()) return
        stopping = true
        val grace = (uncheckedAdvancedConfig as? EnabledAdvancedConfig)?.queue?.shutdownGrace ?: Duration.ZERO
        runBlocking {
            withTimeoutOrNull(grace.toMillis()) {
                poll?.cancelAndJoin()
                attempts.coroutineContext.job.children.toList().joinAll()
            }
            // Bounded as BackgroundScope bounds its own stop: a database that stopped answering would otherwise
            // hold the shutdown for good.
            withTimeoutOrNull(BackgroundScope.STOP_TIMEOUT.toMillis()) {
                reportingFailure("hand back the items this instance holds") {
                    queueItemRepository.release(holder)
                }
            }
        }
    }

    /**
     * Run [block], handing a failure to the condition it says is failing or writing it at WARN as failing to
     * [action]. A cancellation is rethrown.
     */
    private suspend fun reportingFailure(action: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            report(failure, action)
        }
    }

    private fun report(failure: Exception, action: String) {
        if (!conditionMonitor.absorb(failure)) {
            logger.warn(failure) { "Queue: failed to $action." }
        }
    }

    companion object {

        /**
         * How long the item [attemptCount] attempts in waits before its next: `advanced.queue.retry.first-delay`
         * after the first, twice the last after each one after it, and never longer than
         * `advanced.queue.retry.max-delay`.
         */
        internal fun retryDelay(attemptCount: Int, config: QueueConfig): Duration {
            var delay = config.retryFirstDelay
            repeat(attemptCount - 1) {
                if (delay >= config.retryMaxDelay) return config.retryMaxDelay
                delay = delay.multipliedBy(2)
            }
            return minOf(delay, config.retryMaxDelay)
        }
    }
}

/**
 * Add one where that keeps this below [limit], and answer whether it did.
 */
private fun AtomicInteger.tryIncrementBelow(limit: Int): Boolean {
    while (true) {
        val current = get()
        if (current >= limit) return false
        if (compareAndSet(current, current + 1)) return true
    }
}
