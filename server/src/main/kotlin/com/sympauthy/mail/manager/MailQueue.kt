package com.sympauthy.mail.manager

import com.sympauthy.mail.mapper.QueuedMailMapper
import com.sympauthy.mail.model.Mail
import com.sympauthy.mail.model.QueuedMail
import com.sympauthy.platform.queue.manager.QueueManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.util.*

/**
 * Where a mail this server sends is queued, together with the change it reports, for [MailDeliverer] to deliver
 * once that change has committed. What the queue promises is `docs/design/delivery.md`.
 *
 * When mail sending is not configured, the queue is disabled and [send] is a no-op.
 */
@Singleton
class MailQueue(
    @Inject private val mailDeliverer: MailDeliverer,
    @Inject private val queueManager: QueueManager,
    @Inject private val queuedMailMapper: QueuedMailMapper
) {

    /**
     * Whether the mail queue is enabled: whether [MailDeliverer] delivers, which needs a mail server configured.
     */
    val enabled: Boolean get() = mailDeliverer.enabled

    /**
     * Queue the [mail] to [receiver], drawn in [locale] with [parameters], in the transaction the caller runs in, as
     * [QueueManager.enqueue] writes an item: a caller writes it inside the transaction of the change the mail
     * reports.
     *
     * If the queue is not [enabled], this method is a no-op.
     *
     * [maxAge] is how long the mail stays worth sending.
     */
    suspend fun send(
        mail: Mail,
        locale: Locale,
        receiver: String,
        parameters: Map<String, String>,
        maxAge: Duration
    ) {
        if (!enabled) return
        val queuedMail = QueuedMail(mail = mail, receiver = receiver, locale = locale, parameters = parameters)
        queueManager.enqueue(
            destination = MailDeliverer.DESTINATION,
            kind = mail.kind,
            payload = queuedMailMapper.toPayload(queuedMail),
            maxAge = maxAge
        )
    }
}
