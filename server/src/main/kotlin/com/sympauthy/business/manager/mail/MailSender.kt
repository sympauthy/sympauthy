package com.sympauthy.business.manager.mail

import io.micronaut.context.annotation.Requires
import io.micronaut.email.Email
import io.micronaut.email.EmailSender
import io.micronaut.email.javamail.sender.JavaMailConfiguration
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager in-charge of sending mails.
 *
 * As the javamail API is not asynchronous, this manager will schedule the operation on an I/O thread to avoid
 * blocking a main thread. Also, the underlying [EmailSender] MUST never be used directly for this reason.
 */
@Singleton
@Requires(beans = [JavaMailConfiguration::class])
class MailSender(
    @Inject private val sender: EmailSender<Any, Any>
) {

    suspend fun send(builder: Email.Builder) {
        withContext(Dispatchers.IO) {
            sender.send(builder)
        }
    }
}
