package com.sympauthy.mail

import com.sympauthy.server.MailMessages
import io.micronaut.context.MessageSource
import io.micronaut.context.annotation.Value
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Singleton
class TemplatedMailBuilderFactory(
    @param:Value("\${javamail.properties.mail.from}") private val defaultFrom: String?,
    @Inject @param:MailMessages private val messageSource: MessageSource,
    @Inject private val mailBrandResolver: MailBrandResolver
) {

    /**
     * A builder for the mail [template], its model holding the [parameters] the mail was queued with, then what is
     * read when the mail is sent: the brand, the name it is signed under and, for a mail with an [expirationDate],
     * the minutes left before it. Each is written over a queued parameter of the same name, since a mail sent after
     * a restart is drawn in the brand the deployment has then, and a code queued twelve minutes ago has less left
     * than it had.
     */
    fun builder(
        template: String,
        locale: Locale,
        parameters: Map<String, String> = emptyMap(),
        expirationDate: LocalDateTime? = null
    ): TemplatedMailBuilder {
        val builder = TemplatedMailBuilder(
            htmlTemplate = template,
            messageSource = messageSource,
            locale = locale,
        )
        defaultFrom?.let(builder::sender)
        parameters.forEach { (key, value) -> builder.set(key, value) }
        builder.set(TemplatedMailBuilder.BRAND_MODEL_KEY, mailBrandResolver.resolve())
        builder.set(TemplatedMailBuilder.DISPLAY_NAME_MODEL_KEY, mailBrandResolver.displayName)
        expirationDate?.let {
            val left = Duration.between(LocalDateTime.now(), it)
            builder.set(TemplatedMailBuilder.EXPIRY_MINUTES_MODEL_KEY, left.toMinutesRoundedUp().toString())
        }
        return builder
    }

    /**
     * The minutes a reader has left, rounded up so that a code valid for 9 minutes 59 does not read as 9.
     */
    private fun Duration.toMinutesRoundedUp(): Long = (toSeconds() + 59).floorDiv(60).coerceAtLeast(1)
}
