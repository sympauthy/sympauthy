package com.sympauthy.mail.manager

import io.micronaut.context.annotation.Requires
import io.micronaut.email.Email
import io.micronaut.email.EmailSender
import io.micronaut.email.javamail.sender.JavaMailConfiguration
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-level manager in-charge of sending mails via SMTP.
 *
 * As the javamail API is not asynchronous, this manager will schedule the operation on an I/O thread to avoid
 * blocking a main thread. Also, the underlying [EmailSender] MUST never be used directly for this reason.
 *
 * This class should not be used directly by callers. Use [MailQueue] instead, which provides
 * fire-and-forget sending with database-backed crash resilience.
 */
@Singleton
@Requires(beans = [JavaMailConfiguration::class])
class MailSender(
    @Inject private val sender: EmailSender<Any, Any>
) {

    suspend fun send(builder: Email.Builder) {
        withContext(SENDING) {
            sender.send(builder)
        }
    }

    private companion object {

        /**
         * The threads a send blocks: a view of [Dispatchers.IO] of its own, which the rest of the server's blocking
         * calls do not share, so that a mail server that hangs holds none of their threads. How many sends run at
         * once is bounded before this, by `advanced.queue.batch-size`.
         */
        val SENDING: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(64)
    }
}
