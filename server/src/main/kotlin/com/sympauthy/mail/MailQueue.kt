package com.sympauthy.mail

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.business.manager.lock.LeasedJob.MAIL_BACKLOG
import com.sympauthy.client.remoteErrorType
import com.sympauthy.config.ConfigReadiness
import com.sympauthy.data.model.MailQueueEntity
import com.sympauthy.data.repository.MailQueueRepository
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.RemoteCallMonitor
import com.sympauthy.monitoring.loggerForClass
import com.sympauthy.monitoring.telemetry.MailMetrics
import com.sympauthy.monitoring.telemetry.Outcome
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.SendFailedException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import javax.net.ssl.SSLException

/**
 * Mail queue that provides fire-and-forget mail sending with database-backed crash resilience.
 *
 * Mails are persisted to the database and enqueued to an in-process coroutine [Channel] for immediate dispatch.
 * On successful send, the database record is deleted. On failure, the record is discarded from the database.
 *
 * A mail server that cannot be reached is reported to the mail sender's condition; any other mail that
 * fails to send is a WARN about that mail. Every mail handed to the mail server is counted by which [Mail] it
 * is and how the send ended — see `docs/design/telemetry.md`.
 *
 * On startup, unsent mails that have not expired are replayed; expired records are discarded.
 * Mails with no expiration date are always replayed.
 *
 * **The replay is one instance's job, taken under a lease** held over the delivery rather than over the
 * reading of it, as `docs/standards/locking-standard.md` requires of a job that runs once. That is why a
 * replayed mail is sent by the replay itself rather than handed to the channel.
 *
 * The replay asks readiness first, as `docs/standards/config-layer-code-standard.md` requires of startup
 * work, and here for a reason of its own: [processMail] deletes the record whatever the send returned, so a
 * mail replayed there has spent the one attempt it gets — sent from a server reporting itself unready,
 * carrying links to one that answers errors. Left in the table it keeps that attempt, and costs a startup. A
 * mail [send] queues is not gated the same way: that one belongs to a request the server did serve.
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
    @Inject private val jobLeaseManager: JobLeaseManager,
    @Inject private val remoteCallMonitor: RemoteCallMonitor,
    @Inject private val mailMetrics: MailMetrics
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
     * Deliver the backlog, if this instance is the one that gets to take its lease.
     *
     * It runs on the queue's own scope rather than on the thread the readiness event arrived on. The
     * lease is held until the last mail has been attempted, and readiness is not something to hold open
     * for the length of an SMTP conversation.
     */
    internal suspend fun replayBacklog() {
        if (!enabled) return

        if (configReadiness.getConfigurationErrors().isNotEmpty()) {
            logger.warn {
                "Mail queue: not replaying — the configuration has errors and this server reports " +
                        "itself unready. Unsent mails stay in the queue for the next startup."
            }
            return
        }

        jobLeaseManager.withLease(MAIL_BACKLOG) {
            replayUnsentMails()
        }
    }

    /**
     * Persist the [mail] to the database and enqueue it for immediate async sending, to [receiver], drawn in
     * [locale] with [parameters].
     * Returns immediately — the mail is sent in the background.
     *
     * If the queue is not [enabled], this method is a no-op.
     *
     * [maxAge] is how long the mail stays worth sending: one still unsent when the server replays the queue on
     * startup is discarded once it is older than that, rather than reaching a recipient for whom it no longer
     * means anything. A null [maxAge] never expires, and is replayed on every startup until it is sent.
     */
    suspend fun send(
        mail: Mail,
        locale: Locale,
        receiver: String,
        parameters: Map<String, String>,
        maxAge: Duration? = null
    ) {
        if (!enabled) return

        val now = LocalDateTime.now()
        val entity = MailQueueEntity(
            template = mail.templatePath,
            locale = locale.toLanguageTag(),
            receiver = receiver,
            subjectKey = mail.subjectKey,
            parameters = parameters,
            creationDate = now,
            expirationDate = maxAge?.let { now.plus(it) }
        )
        val saved = mailQueueRepository.save(entity)
        channel.send(saved.toQueuedMail())
    }

    /**
     * Discard the mails that expired while unsent, then deliver the ones that did not. Each is sent here
     * rather than queued for the consumer, so that the caller's lease covers the delivery.
     */
    internal suspend fun replayUnsentMails() {
        val now = LocalDateTime.now()
        val staleCount = mailQueueRepository.deleteByExpirationDateBefore(now)
        if (staleCount > 0) {
            logger.info { "Discarded $staleCount expired mail(s) from queue." }
        }

        val unsent = mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(now)
        for (entity in unsent) {
            processMail(entity.toQueuedMail())
        }
        if (unsent.isNotEmpty()) {
            logger.info { "Replayed ${unsent.size} unsent mail(s) from database." }
        }
    }

    private suspend fun processMail(mail: QueuedMail) {
        try {
            val builder = mailBuilderFactory
                .builder(
                    template = mail.template,
                    locale = mail.locale,
                    parameters = mail.parameters,
                    expirationDate = mail.expirationDate
                )
                .apply {
                    receiver(mail.receiver)
                    localizedSubject(mail.subjectKey)
                }
            logger.trace { "Sending mail template=${mail.template} to=${mail.receiver}." }
            send(mail, builder)
        } catch (e: Exception) {
            if (!e.isMailServerFailure()) {
                logger.warn(e) {
                    "Failed to send mail ${mail.id} (template=${mail.template}, receiver=${mail.receiver})."
                }
            }
        } finally {
            mailQueueRepository.deleteById(mail.id)
        }
    }

    /**
     * Hand the [mail] [builder] built to the mail server, report the call to the mail sender's condition and
     * count the send, as [mailOutcomeOrNull] judges what it threw.
     *
     * A row a previous build queued under a template this one draws no [Mail] from is reported to the condition
     * and not counted: the set a send is counted under is the one this build ships.
     */
    private suspend fun send(mail: QueuedMail, builder: TemplatedMailBuilder) {
        val start = System.nanoTime()
        val sent = Mail.ofTemplatePathOrNull(mail.template)
        try {
            mailSender!!.send(builder.builder)
        } catch (e: Exception) {
            e.mailOutcomeOrNull()?.let { outcome ->
                remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, System.nanoTime() - start, outcome, e)
                sent?.let { mailMetrics.recordSend(it.metricName, outcome) }
            }
            throw e
        }
        remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, System.nanoTime() - start, Outcome.Success)
        sent?.let { mailMetrics.recordSend(it.metricName, Outcome.Success) }
    }

    /**
     * How a send that failed with this is recorded as a call to the mail server: an error where the server is
     * unwell, as [isMailServerFailure] says, a refusal where it answered by refusing the mail or its recipients,
     * and null where it was never asked — the mail failed to build.
     *
     * The sender wraps whatever JavaMail threw, sending or composing alike, so a refusal is told by the
     * [SendFailedException] only the server's answer raises, not by any [jakarta.mail.MessagingException].
     */
    private fun Exception.mailOutcomeOrNull(): Outcome? {
        if (isMailServerFailure()) return Outcome.Error(mailServerErrorType())
        return generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<SendFailedException>()
            .firstOrNull()
            ?.let { Outcome.Refused(it.javaClass.name) }
    }

    /**
     * Whether this failure to send a mail says the mail server is unwell — it could not be reached, did not
     * answer in time, or refused this server's credentials — rather than that this one mail could not be
     * sent.
     *
     * The network's own failures are named rather than any [java.io.IOException]: a template that cannot be
     * read fails with one too, inside the same send, and is this one mail's.
     */
    private fun Exception.isMailServerFailure(): Boolean = generateSequence<Throwable>(this) { it.cause }
        .any {
            it is SocketException || it is SocketTimeoutException || it is UnknownHostException ||
                it is SSLException || it is AuthenticationFailedException
        }

    /**
     * What a failure [isMailServerFailure] holds for is typed by on a metric: `authentication` where the server
     * refused this server's credentials, and what [remoteErrorType] types any other remote call's by otherwise.
     */
    private fun Exception.mailServerErrorType(): String =
        if (generateSequence<Throwable>(this) { it.cause }.any { it is AuthenticationFailedException }) {
            AUTHENTICATION
        } else {
            remoteErrorType()
        }

    private fun MailQueueEntity.toQueuedMail() = QueuedMail(
        id = id!!,
        template = template,
        locale = Locale.forLanguageTag(locale),
        receiver = receiver,
        subjectKey = subjectKey,
        parameters = parameters,
        expirationDate = expirationDate
    )

    companion object {
        /**
         * The operation a call to the mail server is recorded under.
         */
        private const val SEND_OPERATION = "send"

        /**
         * What a send the mail server refused this server's credentials for is typed by.
         */
        private const val AUTHENTICATION = "authentication"
    }
}
