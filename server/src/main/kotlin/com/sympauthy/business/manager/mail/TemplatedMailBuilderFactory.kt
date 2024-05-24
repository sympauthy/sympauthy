package com.sympauthy.business.manager.mail

import com.sympauthy.server.MailMessages
import io.micronaut.context.MessageSource
import io.micronaut.context.annotation.Value
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Singleton
class TemplatedMailBuilderFactory(
    @Value("\${javamail.properties.mail.from}") private val defaultFrom: String?,
    @Inject @MailMessages private val messageSource: MessageSource
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
        defaultFrom?.let(builder::sender) // FIXME Parse from to get email and name.
        return builder
    }
}
