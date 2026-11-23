package com.sympauthy.mail.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.mail.model.Mail
import com.sympauthy.mail.model.QueuedMail
import com.sympauthy.platform.queue.model.QueueItem
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.util.*

@Singleton
class QueuedMailMapper(
    @Inject private val objectMapper: ObjectMapper
) {

    /**
     * The payload a queue item carrying [queuedMail] is written with: everything but its [Mail], which is the
     * item's kind.
     */
    fun toPayload(queuedMail: QueuedMail): String = objectMapper.writeValueAsString(
        Payload(
            receiver = queuedMail.receiver,
            locale = queuedMail.locale.toLanguageTag(),
            parameters = queuedMail.parameters
        )
    )

    /**
     * The mail [item] carries.
     *
     * Throws `mapper.queued_mail.invalid_property`, internal, where the item's kind names no [Mail] this build
     * sends — one a previous build queued — or where its payload does not read as a mail's.
     */
    fun toQueuedMail(item: QueueItem): QueuedMail {
        val mail = Mail.ofKindOrNull(item.kind) ?: throw invalidProperty("kind")
        val payload = try {
            objectMapper.readValue(item.payload, Payload::class.java)
        } catch (failure: IOException) {
            throw invalidProperty("payload", failure)
        } ?: throw invalidProperty("payload")
        return QueuedMail(
            mail = mail,
            receiver = payload.receiver,
            locale = Locale.forLanguageTag(payload.locale),
            parameters = payload.parameters
        )
    }

    private fun invalidProperty(invalidProperty: String, cause: Throwable? = null): BusinessException =
        internalBusinessExceptionOf(
            detailsId = "mapper.queued_mail.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )

    /**
     * The JSON document a queued mail's payload is, the locale written as a language tag.
     */
    @Serdeable
    internal data class Payload(
        val receiver: String,
        val locale: String,
        val parameters: Map<String, String>
    )
}
