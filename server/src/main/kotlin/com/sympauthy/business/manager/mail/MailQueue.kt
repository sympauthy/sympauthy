package com.sympauthy.business.manager.mail

import com.sympauthy.business.model.mail.QueuedMail
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
 * When mail sending is not configured (no [MailSender] available), the queue is disabled
 * and [send] is a no-op.
 */
@Singleton
class MailQueue(
    @Inject private val mailSender: MailSender?,
    @Inject private val mailBuilderFactory: TemplatedMailBuilderFactory,
    @Inject private val mailQueueRepository: MailQueueRepository
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
        if (!enabled) return

        runBlocking {
            launch {
                replayUnsentMails()
            }
        }
    }

    /**
     * Persist the mail to the database and enqueue it for immediate async sending.
     * Returns immediately — the mail is sent in the background.
     *
     * If the queue is not [enabled], this method is a no-op.
     *
     * @param maxAge If provided, the mail will be discarded on startup replay if older than this duration.
     *               If null, the mail never expires and will always be replayed.
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

    private suspend fun replayUnsentMails() {
        val now = LocalDateTime.now()
        val staleCount = mailQueueRepository.deleteByExpirationDateBefore(now)
        if (staleCount > 0) {
            logger.info("Discarded $staleCount expired mail(s) from queue.")
        }

        val unsent = mailQueueRepository.findByExpirationDateIsNullOrExpirationDateAfter(now)
        for (entity in unsent) {
            channel.send(entity.toQueuedMail())
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
