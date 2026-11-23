package com.sympauthy.mail

import com.sympauthy.data.currentConnectionOrNull
import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.data.repository.QueueItemRepository
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.data.connection.ConnectionSynchronization
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * Where a mail this server sends is written, together with the change it reports, for [MailDelivery] to deliver
 * once that change has committed. What the queue promises is `docs/design/delivery.md`.
 *
 * When mail sending is not configured (no [MailSender] available), the queue is disabled and [send] is a no-op.
 */
@Singleton
class MailQueue(
    @Inject private val mailSender: MailSender?,
    @Inject private val queueItemRepository: QueueItemRepository,
    @Inject private val objectMapper: ObjectMapper,
    @Inject private val mailDelivery: MailDelivery
) : ApplicationEventListener<ServiceReadyEvent> {

    /**
     * Whether the mail queue is enabled. The queue is enabled when a [MailSender] is available,
     * which requires the JavaMail configuration to be present.
     */
    val enabled: Boolean get() = mailSender != null

    override fun onApplicationEvent(event: ServiceReadyEvent) {
        mailDelivery.startPolling()
    }

    /**
     * Write the [mail] to [receiver], drawn in [locale] with [parameters], in the transaction the caller runs in,
     * and have this instance attempt it as soon as that transaction has committed.
     *
     * A caller writes it inside the transaction of the change the mail reports: a change that rolls back takes its
     * mail with it, and one that commits leaves the mail to be delivered by whichever instance looks next, should
     * this one die before it attempts it. A caller outside a transaction commits the mail on its own.
     *
     * The attempt is given when the transaction's connection closes, which it does once it has committed or rolled
     * back; after a rollback, it finds nothing.
     *
     * If the queue is not [enabled], this method is a no-op.
     *
     * [maxAge] is how long the mail stays worth sending: a mail whose next attempt would come later than that is
     * given up rather than reaching a recipient for whom it no longer means anything. A null [maxAge] never
     * expires, and is attempted until it is delivered.
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
        val payload = MailPayload(receiver = receiver, locale = locale.toLanguageTag(), parameters = parameters)
        val entity = QueueItemEntity(
            destination = MailDelivery.DESTINATION,
            kind = mail.metricName,
            payload = objectMapper.writeValueAsString(payload),
            creationDate = now,
            expirationDate = maxAge?.let { now.plus(it) }
        )
        val id = queueItemRepository.save(entity).id!!
        val connection = currentConnectionOrNull()
        if (connection == null) {
            mailDelivery.nudge(id)
        } else {
            connection.registerSynchronization(object : ConnectionSynchronization {
                override fun afterClosed() {
                    mailDelivery.nudge(id)
                }
            })
        }
    }
}
