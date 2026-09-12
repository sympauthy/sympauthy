package com.sympauthy.business.manager.mail

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.business.manager.lock.LeasedJob.MAIL_BACKLOG
import com.sympauthy.business.model.mail.QueuedMail
import com.sympauthy.config.ConfigReadiness
import com.sympauthy.data.model.MailQueueEntity
import com.sympauthy.data.repository.MailQueueRepository
import com.sympauthy.util.loggerForClass
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * Mail queue that provides fire-and-forget mail sending with database-backed crash resilience.
 *
 * Mails are persisted to the database and enqueued to an in-process coroutine [Channel] for immediate dispatch.
 * On successful send, the database record is deleted. On failure, the record is discarded from the database.
 *
 * On startup, unsent mails that have not expired are replayed; expired records are discarded.
 * Mails with no expiration date are always replayed.
 *
 * **The replay is one instance's job, taken under a lease.** Without it every instance of a deployment
 * replays the same rows and every recipient is sent their validation code once per instance. The lease
 * is held for the whole of the delivery rather than for the reading of it: the rows are deleted as they
 * are attempted, so a lease released while they were still in a queue would leave the next instance to
 * boot a backlog it would send again. That is why a replayed mail is sent by the replay itself rather
 * than handed to the channel — see [com.sympauthy.business.manager.lock.JobLeaseManager].
 *
 * The replay does not run on a deployment whose configuration has errors. [processMail] deletes the record
 * whatever the send returned, so a mail replayed there has spent the one attempt it gets — sent from a
 * server reporting itself unready, carrying links to one that answers errors. Left in the table it keeps
 * that attempt, and costs a startup. A mail [send] queues is not gated the same way: that one belongs to a
 * request the server did serve.
 *
 * When mail sending is not configured (no [MailSender] available), the queue is disabled
 * and [send] is a no-op.
 */
@Singleton
class MailQueue(
    @Inject private val mailSender: MailSender?,
    @Inject private val configReadiness: ConfigReadiness,
    @Inject private val mailBuilderFactory: TemplatedMailBuilderFactory,
    @Inject private val mailQueueRepository: MailQueueRepository,
    @Inject private val jobLeaseManager: JobLeaseManager
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()
    private val channel = Channel<QueuedMail>(capacity = Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Whether the mail queue is enabled. The queue is enabled when a [MailSender] is available,
     * which requires the JavaMail configuration to be present.
     */
    val enabled: Boolean get() = mailSender != null

    init {
        if (enabled) {
            scope.launch {
                for (mail in channel) {
                    processMail(mail)
                }
            }
        }
    }

    override fun onApplicationEvent(event: ServiceReadyEvent) {
        scope.launch { replayBacklog() }
    }

    /**
     * Deliver the backlog, if this instance is the one that gets to: the deployment's other instances
     * hold the same rows and would send every one of them again.
     *
     * It runs on the queue's own scope rather than on the thread the readiness event arrived on. The
     * lease is held until the last mail has been attempted, and readiness is not something to hold open
     * for the length of an SMTP conversation.
     */
    internal suspend fun replayBacklog() {
        if (!enabled) return

        if (configReadiness.getConfigurationErrors().isNotEmpty()) {
            logger.warn(
                "Mail queue: not replaying — the configuration has errors and this server reports " +
                        "itself unready. Unsent mails stay in the queue for the next startup."
            )
            return
        }

        jobLeaseManager.withLease(MAIL_BACKLOG) {
            replayUnsentMails()
        }
    }

    /**
     * Persist the mail to the database and enqueue it for immediate async sending.
     * Returns immediately — the mail is sent in the background.
     *
     * If the queue is not [enabled], this method is a no-op.
     *
     * [maxAge] is how long the mail stays worth sending: one still unsent when the server replays the queue on
     * startup is discarded once it is older than that, rather than reaching a recipient for whom it no longer
     * means anything. A null [maxAge] never expires, and is replayed on every startup until it is sent.
     */
    suspend fun send(
        template: String,
        locale: Locale,
        receiver: String,
        subjectKey: String,
        parameters: Map<String, String>,
        maxAge: Duration? = null
    ) {
        if (!enabled) return

        val now = LocalDateTime.now()
        val entity = MailQueueEntity(
            template = template,
            locale = locale.toLanguageTag(),
            receiver = receiver,
            subjectKey = subjectKey,
            parameters = parameters,
            creationDate = now,
            expirationDate = maxAge?.let { now.plus(it) }
        )
        val saved = mailQueueRepository.save(entity)
        channel.send(saved.toQueuedMail())
    }

    /**
     * Discard the mails that expired while unsent, then deliver the ones that did not.
     *
     * Each is sent here rather than queued for the consumer, because the caller's lease has to cover the
     * delivery: a lease released once the rows were read would let the next instance to become ready read
     * the same rows and send them a second time.
     */
    internal suspend fun replayUnsentMails() {
        val now = LocalDateTime.now()
        val staleCount = mailQueueRepository.deleteByExpirationDateBefore(now)
        if (staleCount > 0) {
            logger.info("Discarded $staleCount expired mail(s) from queue.")
        }

        val unsent = mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(now)
        for (entity in unsent) {
            processMail(entity.toQueuedMail())
        }
        if (unsent.isNotEmpty()) {
            logger.info("Replayed ${unsent.size} unsent mail(s) from database.")
        }
    }

    private suspend fun processMail(mail: QueuedMail) {
        try {
            val builder = mailBuilderFactory
                .builder(template = mail.template, locale = mail.locale)
                .apply {
                    receiver(mail.receiver)
                    mail.parameters.forEach { (key, value) -> set(key, value) }
                    localizedSubject(mail.subjectKey)
                }
            logger.trace("Sending mail template=${mail.template} to=${mail.receiver}.")
            mailSender!!.send(builder.builder)
        } catch (e: Exception) {
            logger.error("Failed to send mail ${mail.id} (template=${mail.template}, receiver=${mail.receiver}).", e)
        } finally {
            mailQueueRepository.deleteById(mail.id)
        }
    }

    private fun MailQueueEntity.toQueuedMail() = QueuedMail(
        id = id!!,
        template = template,
        locale = Locale.forLanguageTag(locale),
        receiver = receiver,
        subjectKey = subjectKey,
        parameters = parameters
    )
}
