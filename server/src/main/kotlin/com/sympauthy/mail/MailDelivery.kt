package com.sympauthy.mail

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.config.ConfigReadiness
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.QueueConfig
import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.data.repository.QueueItemRepository
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.RemoteCallMonitor
import com.sympauthy.monitoring.loggerForClass
import com.sympauthy.monitoring.telemetry.MailMetrics
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.QueueMetrics
import com.sympauthy.monitoring.telemetry.QueueMetrics.Companion.EXPIRED
import com.sympauthy.server.BackgroundScope
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.core.order.Ordered
import io.micronaut.serde.ObjectMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Delivers the mails [MailQueue] wrote as items of the queue, at least once, retrying each until it goes through or
 * expires, and sharing the work with every other instance of the deployment. What it promises, and why, is
 * `docs/design/delivery.md`.
 *
 * **A mail is attempted under a lease on its row**, held by [JobLeaseManager.holder] and renewed with the job
 * leases for as long as this instance is alive, so no other instance attempts it meanwhile and one that died leaves
 * it to be taken once the lease lapses. Between two attempts nobody holds it.
 *
 * **A mail is attempted as soon as its commit returns**, on the [nudge] the queue gives, and otherwise by the poll
 * every instance runs every `advanced.queue.poll-interval`: what an instance left when it died, a retry whose time
 * has come, a nudge that never came. A nudge's attempt never waits behind the poll's.
 *
 * Every coroutine it starts reports its own failure: they are launched in [BackgroundScope], which installs no
 * handler.
 */
@Singleton
open class MailDelivery(
    @Inject private val mailSender: MailSender?,
    @Inject private val configReadiness: ConfigReadiness,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig,
    @Inject private val mailBuilderFactory: TemplatedMailBuilderFactory,
    @Inject private val queueItemRepository: QueueItemRepository,
    @Inject private val objectMapper: ObjectMapper,
    @Inject private val jobLeaseManager: JobLeaseManager,
    @Inject private val backgroundScope: BackgroundScope,
    @Inject private val conditionMonitor: ConditionMonitor,
    @Inject private val remoteCallMonitor: RemoteCallMonitor,
    @Inject private val mailMetrics: MailMetrics,
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
     * How many mails this instance holds from its polls, which `advanced.queue.batch-size` bounds.
     */
    private val heldFromPolls = AtomicInteger()

    @Volatile
    private var poll: Job? = null

    @Volatile
    private var stopping = false

    private val holder: String
        get() = jobLeaseManager.holder

    /**
     * Whether mails are delivered at all, which needs a mail server configured.
     */
    val enabled: Boolean get() = mailSender != null

    /**
     * Start the poll on this instance, unless the server reports itself unready.
     *
     * Configuration is read once, so an unready server stays unready until it restarts, and the poll is not
     * started at all: the mails waiting stay where they are, since one sent from a server answering errors carries
     * links to it. That is said once, as `docs/standards/config-layer-code-standard.md` has work that obeys
     * readiness say it.
     */
    fun startPolling() {
        if (!enabled) return
        poll = backgroundScope.launch { pollUntilStopped() }
    }

    /**
     * Poll every `advanced.queue.poll-interval` until cancelled, or not at all where the server reports itself
     * unready.
     */
    internal suspend fun pollUntilStopped() {
        if (configReadiness.getConfigurationErrors().isNotEmpty()) {
            logger.warn {
                "Mail queue: not polling — the configuration has errors and this server reports itself " +
                    "unready. Waiting mails stay in the queue until a server that is ready delivers them."
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
     * Attempt the mail [id] now, on this instance, once the commit that wrote it has returned.
     *
     * It takes the mail only where nobody has, and never attempted it: a mail whose transaction rolled back is
     * found by nothing, and one another instance's poll took first is that instance's.
     */
    fun nudge(id: UUID) {
        if (!enabled || stopping) return
        attempts.launch {
            reportingFailure("attempt mail $id") {
                val now = queueItemRepository.now()
                if (queueItemRepository.takeUnattempted(id, holder, now.plus(JobLeaseManager.GRACE_PERIOD)) == 1) {
                    queueItemRepository.findById(id)?.let { attempt(it) }
                }
            }
        }
    }

    /**
     * Publish the backlog, then claim as many due mails as this instance's polls leave room for, and attempt each.
     */
    internal suspend fun pollOnce(config: QueueConfig) {
        reportingFailure("poll the mail queue") {
            queueMetrics.recordBacklog(readBacklog())
            val free = config.batchSize - heldFromPolls.get()
            if (free <= 0 || stopping) return@reportingFailure
            claimDue(free).forEach { item ->
                heldFromPolls.incrementAndGet()
                attempts.launch {
                    try {
                        reportingFailure("attempt mail ${item.id}") { attempt(item) }
                    } finally {
                        heldFromPolls.decrementAndGet()
                    }
                }
            }
        }
    }

    /**
     * Claim up to [limit] mails that are due and held by nobody, or by an instance whose lease lapsed, and hold them
     * for this instance.
     *
     * The claim and the hold are one transaction: outside one, the rows the claim locks are released by the
     * autocommit that took them, and an instance polling at the same moment claims the same mails.
     */
    @Transactional
    internal open suspend fun claimDue(limit: Int): List<QueueItemEntity> {
        val now = queueItemRepository.now()
        val claimed = queueItemRepository.claimDue(DESTINATION, now, limit)
        if (claimed.isEmpty()) return claimed
        queueItemRepository.holdClaimed(
            ids = claimed.mapNotNull(QueueItemEntity::id),
            holder = holder,
            now = now,
            expiresAt = now.plus(JobLeaseManager.GRACE_PERIOD)
        )
        return claimed
    }

    /**
     * Push back every lease this instance holds on a mail, as [JobLeaseManager.renewLeases] does a job's.
     */
    suspend fun renewLeases() {
        if (!enabled) return
        reportingFailure("renew the mail queue's leases") {
            val now = queueItemRepository.now()
            queueItemRepository.renew(holder, now, now.plus(JobLeaseManager.GRACE_PERIOD))
        }
    }

    /**
     * Attempt the mail [item] carries, an item this instance holds, and leave it delivered, waiting for a retry, or
     * given up.
     *
     * An item of a kind this build sends no mail of, or whose payload it cannot read, cannot be built, and is given up
     * as a mail that cannot be.
     */
    internal suspend fun attempt(item: QueueItemEntity) {
        val kind = Mail.ofKindOrNull(item.kind)
        val expirationDate = item.expirationDate
        if (expirationDate != null && expirationDate.isBefore(queueItemRepository.now())) {
            giveUpExpired(item, kind)
            return
        }
        val start = System.nanoTime()
        val failure = try {
            mailSender!!.send(build(item, kind).builder)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failure
        }
        val elapsed = System.nanoTime() - start
        if (failure == null) {
            remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, elapsed, Outcome.Success)
            kind?.let { mailMetrics.recordSend(it.metricName, Outcome.Success) }
            queueItemRepository.deleteHeld(item.id!!, holder)
            return
        }
        when (val classified = MailFailure.of(failure)) {
            is MailFailure.MailServerUnwell -> {
                val outcome = Outcome.Error(classified.errorType)
                remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, elapsed, outcome, failure)
                kind?.let { mailMetrics.recordSend(it.metricName, outcome) }
                retry(item, kind)
            }

            is MailFailure.TemporaryRefusal -> {
                val replyCode = classified.replyCode.toString()
                remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, elapsed, Outcome.Refused(replyCode))
                kind?.let { mailMetrics.recordSend(it.metricName, Outcome.Error(replyCode)) }
                retry(item, kind)
            }

            is MailFailure.PermanentRefusal -> {
                val outcome = Outcome.Refused(classified.replyCode.toString())
                remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, elapsed, outcome)
                kind?.let { mailMetrics.recordSend(it.metricName, outcome) }
                logger.debug {
                    "Gave up mail ${item.id} (kind=${item.kind}): the mail server refused it with " +
                        "${classified.replyCode}."
                }
                queueItemRepository.deleteHeld(item.id!!, holder)
            }

            MailFailure.Unclassified -> {
                if (!conditionMonitor.absorb(failure)) {
                    logger.warn(failure) { "Gave up mail ${item.id} (kind=${item.kind}): it failed to send." }
                }
                kind?.let { queueMetrics.recordGivenUp(DESTINATION, it.metricName, Outcome.errorOf(failure)) }
                queueItemRepository.deleteHeld(item.id!!, holder)
            }
        }
    }

    private fun build(item: QueueItemEntity, kind: Mail?): TemplatedMailBuilder {
        val mail = kind ?: error("This build sends no mail of kind ${item.kind}.")
        val payload = objectMapper.readValue(item.payload, MailPayload::class.java)
            ?: error("Mail ${item.id} holds no payload.")
        return mailBuilderFactory
            .builder(
                template = mail.templatePath,
                locale = Locale.forLanguageTag(payload.locale),
                parameters = payload.parameters,
                expirationDate = item.expirationDate
            )
            .apply {
                receiver(payload.receiver)
                localizedSubject(mail.subjectKey)
            }
    }

    /**
     * Leave [item] for its next attempt, or give it up where that attempt would fall after its expiry.
     *
     * Where the configuration is not usable, the mail is handed back due at once, for an instance that is ready:
     * this one does not poll.
     */
    private suspend fun retry(item: QueueItemEntity, kind: Mail?) {
        val attemptCount = item.attemptCount + 1
        val now = queueItemRepository.now()
        val config = (uncheckedAdvancedConfig as? EnabledAdvancedConfig)?.queue
        val nextAttemptDate = config?.let { now.plus(retryDelay(attemptCount, it)) } ?: now
        val expirationDate = item.expirationDate
        if (expirationDate != null && nextAttemptDate.isAfter(expirationDate)) {
            giveUpExpired(item, kind)
            return
        }
        queueItemRepository.reschedule(item.id!!, holder, attemptCount, nextAttemptDate)
    }

    private suspend fun giveUpExpired(item: QueueItemEntity, kind: Mail?) {
        logger.debug { "Gave up mail ${item.id} (kind=${item.kind}): it expired before it went through." }
        kind?.let { queueMetrics.recordGivenUp(DESTINATION, it.metricName, Outcome.Error(EXPIRED)) }
        queueItemRepository.deleteHeld(item.id!!, holder)
    }

    /**
     * How many mails wait, and for how long the oldest has, for every [Mail] this build sends.
     */
    private suspend fun readBacklog(): List<QueueMetrics.Backlog> {
        val now = LocalDateTime.now()
        val byKind = queueItemRepository.summarize()
            .filter { it.destination == DESTINATION }
            .mapNotNull { summary -> Mail.ofKindOrNull(summary.kind)?.let { it to summary } }
            .toMap()
        return Mail.entries.map { kind ->
            val summary = byKind[kind]
            QueueMetrics.Backlog(
                destination = DESTINATION,
                kind = kind.metricName,
                pending = summary?.pendingCount ?: 0,
                oldestAgeSeconds = summary?.let { Duration.between(it.oldestCreationDate, now).seconds } ?: 0
            )
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
     * Stop taking mails, let the attempts under way finish for `advanced.queue.shutdown-grace`, then hand back every
     * mail this instance still holds, so another instance takes it on its next poll rather than once the lease
     * lapses. An attempt still running then may deliver its mail after another instance has: the most that is lost
     * is a second copy.
     */
    override fun onApplicationEvent(event: ShutdownEvent) {
        if (!enabled) return
        stopping = true
        val grace = (uncheckedAdvancedConfig as? EnabledAdvancedConfig)?.queue?.shutdownGrace ?: Duration.ZERO
        runBlocking {
            poll?.cancelAndJoin()
            withTimeoutOrNull(grace.toMillis()) {
                attempts.coroutineContext.job.children.toList().joinAll()
            }
            reportingFailure("hand back the mails this instance holds") {
                queueItemRepository.release(holder)
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
            if (!conditionMonitor.absorb(failure)) {
                logger.warn(failure) { "Mail queue: failed to $action." }
            }
        }
    }

    companion object {

        /**
         * The destination every mail is delivered to, as its queue item is written with and the queue's metrics
         * record it.
         */
        const val DESTINATION = "mail"

        /**
         * The operation a call to the mail server is recorded under.
         */
        private const val SEND_OPERATION = "send"

        /**
         * How long the mail [attemptCount] attempts in waits before its next: `advanced.queue.retry.first-delay`
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
