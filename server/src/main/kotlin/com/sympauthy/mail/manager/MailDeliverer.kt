package com.sympauthy.mail.manager

import com.sympauthy.exception.toOutcome
import com.sympauthy.mail.mapper.QueuedMailMapper
import com.sympauthy.mail.model.Mail
import com.sympauthy.mail.model.MailFailure
import com.sympauthy.mail.model.QueuedMail
import com.sympauthy.platform.monitoring.loggerForClass
import com.sympauthy.platform.monitoring.manager.ConditionMonitor
import com.sympauthy.platform.monitoring.manager.RemoteCallMonitor
import com.sympauthy.platform.monitoring.model.Condition
import com.sympauthy.platform.monitoring.model.Outcome
import com.sympauthy.platform.monitoring.telemetry.manager.MailMetrics
import com.sympauthy.platform.queue.manager.QueueItemDeliverer
import com.sympauthy.platform.queue.model.DeliveryAttempt
import com.sympauthy.platform.queue.model.QueueItem
import io.micronaut.email.EmailException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Delivers the mails [MailQueue] queued to the mail server: draws each at its attempt, sends it, and reads what the
 * mail server answered as whether the mail is retried. What the answers mean is `docs/design/mail.md`.
 */
@Singleton
class MailDeliverer(
    @Inject private val mailSender: MailSender?,
    @Inject private val mailBuilderFactory: TemplatedMailBuilderFactory,
    @Inject private val queuedMailMapper: QueuedMailMapper,
    @Inject private val conditionMonitor: ConditionMonitor,
    @Inject private val remoteCallMonitor: RemoteCallMonitor,
    @Inject private val mailMetrics: MailMetrics
) : QueueItemDeliverer {

    private val logger = loggerForClass()

    override val destination: String = DESTINATION

    override val kinds: Set<String> = Mail.entries.mapTo(LinkedHashSet(), Mail::kind)

    /**
     * Whether mails are delivered at all, which needs a mail server configured.
     */
    override val enabled: Boolean get() = mailSender != null

    /**
     * Draw and send the mail [item] carries.
     *
     * An item of a kind this build sends no mail of, or whose payload it cannot read, cannot be drawn, and is
     * answered as a mail that cannot be: a failure of this server's own.
     */
    override suspend fun attempt(item: QueueItem): DeliveryAttempt {
        val kind = Mail.ofKindOrNull(item.kind)
        val start = System.nanoTime()
        val failure = try {
            mailSender!!.send(build(queuedMailMapper.toQueuedMail(item), item).builder)
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failure
        }
        val elapsed = System.nanoTime() - start
        if (failure == null) {
            remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, elapsed, Outcome.Success)
            kind?.let { mailMetrics.recordSend(it.kind, Outcome.Success) }
            return DeliveryAttempt.Delivered
        }
        return when (val classified = MailFailure.of(failure)) {
            is MailFailure.MailServerUnwell -> {
                val outcome = Outcome.Error(classified.errorType)
                remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, elapsed, outcome, failure)
                kind?.let { mailMetrics.recordSend(it.kind, outcome) }
                DeliveryAttempt.Retry
            }

            is MailFailure.TemporaryRefusal -> {
                val replyCode = classified.replyCode.toString()
                remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, elapsed, Outcome.Refused(replyCode))
                kind?.let { mailMetrics.recordSend(it.kind, Outcome.Error(replyCode)) }
                DeliveryAttempt.Retry
            }

            is MailFailure.PermanentRefusal -> {
                val outcome = Outcome.Refused(classified.replyCode.toString())
                remoteCallMonitor.reportCall(Condition.Mail, SEND_OPERATION, elapsed, outcome)
                kind?.let { mailMetrics.recordSend(it.kind, outcome) }
                logger.debug {
                    "Gave up mail ${item.id} (kind=${item.kind}): the mail server refused it with " +
                        "${classified.replyCode}."
                }
                DeliveryAttempt.Refused
            }

            MailFailure.Unclassified -> {
                if (!conditionMonitor.absorb(failure)) {
                    logger.warn(failure) { "Gave up mail ${item.id} (kind=${item.kind}): it failed to send." }
                }
                DeliveryAttempt.Failed(failure.unwrapped().toOutcome())
            }
        }
    }

    /**
     * The failure a send raised, as JavaMail or the template raised it: the mail sender wraps every one in the same
     * [EmailException], which says nothing of its own.
     */
    private fun Exception.unwrapped(): Throwable = if (this is EmailException) cause ?: this else this

    private fun build(queuedMail: QueuedMail, item: QueueItem): TemplatedMailBuilder = mailBuilderFactory
        .builder(
            template = queuedMail.mail.templatePath,
            locale = queuedMail.locale,
            parameters = queuedMail.parameters,
            expirationDate = item.expirationDate
        )
        .apply {
            receiver(queuedMail.receiver)
            localizedSubject(queuedMail.mail.subjectKey)
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
    }
}
