package com.sympauthy.mail

import com.sympauthy.server.MailMessages
import io.micronaut.context.MessageSource
import io.micronaut.context.annotation.Value
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Singleton
class TemplatedMailBuilderFactory(
    @param:Value("\${javamail.properties.mail.from}") private val defaultFrom: String?,
    @Inject @param:MailMessages private val messageSource: MessageSource,
    @Inject private val mailBrandResolver: MailBrandResolver
) {

    fun builder(
        template: String,
        locale: Locale
    ): TemplatedMailBuilder {
        val builder = TemplatedMailBuilder(
            htmlTemplate = template,
            messageSource = messageSource,
            locale = locale,
        )
        defaultFrom?.let(builder::sender)
        // The brand is the deployment's at the time the mail is sent, not when it was queued.
        builder.set(TemplatedMailBuilder.BRAND_MODEL_KEY, mailBrandResolver.resolve())
        builder.set(TemplatedMailBuilder.DISPLAY_NAME_MODEL_KEY, mailBrandResolver.displayName)
        return builder
    }
}
