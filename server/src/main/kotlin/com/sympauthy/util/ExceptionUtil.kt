package com.sympauthy.util

import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.exception.LocalizedException
import io.micronaut.context.MessageSource
import io.micronaut.data.exceptions.EntityExistsException
import java.util.*

/**
 * Extracts a key and a localized error message from the [Exception] and return a pair consisting of:
 * - The key that identifies or represents the error.
 * - A localized message that provides additional details about the error, or null if unavailable.
 *
 * For [LocalizedException], the method retrieves a custom key from the [Exception]'s values and uses the details ID
 * to fetch a localized message.
 * For [ConfigurationException], it uses the exception's key and message ID for localization.
 * For generic exceptions, it defaults to the class name of the exception and its message.
 */
fun Exception.getKeyAndLocalizedMessage(messageSource: MessageSource): Pair<String, String?> {
    return when (this) {
        is LocalizedException -> {
            val key = values["key"] ?: "unknown"
            key to messageSource.render(detailsId, values)
        }

        is ConfigurationException -> key to messageSource.render(messageId, values)

        else -> javaClass.name to message
    }
}

/**
 * Whether the write was refused because a row for the key it carried is already there, which is the
 * violation `docs/locking-standard.md` has the manager writing the row translate.
 *
 * This is narrower than the integrity failures the driver raises one exception for: a foreign key with
 * nothing behind it and a column refusing null are this server writing a row it should not have, and
 * they travel on rather than being answered as something the caller sent twice.
 *
 * The cause chain is walked rather than the type matched, because the failure reaches a manager through
 * a transaction interceptor and a coroutine bridge — either of which may wrap it, and a wrapped one a
 * manager did not recognise would silently lose the translation.
 */
internal fun Throwable.isRowAlreadyThere(): Boolean =
    generateSequence(this, Throwable::cause)
        .take(CAUSE_DEPTH)
        .any { it is EntityExistsException }

/** Bounded, so an exception whose cause is itself cannot hold the walk open. */
private const val CAUSE_DEPTH = 10

/**
 * Renders the message [messageId] names in [locale], interpolating [values] into its placeholders,
 * or returns null when the bundle holds no such message.
 *
 * This is the only place a message is asked for, and [values] may not admit a null, which is what
 * makes that worth insisting on. The message source takes named values and positional values through
 * two functions of the same name, and a map whose values are nullable matches only the positional one
 * — so the call binds there, silently, and the message is rendered with no named value in scope at
 * all. Every placeholder in it then resolves to nothing and is written out as its own name, which is
 * what a reader gets instead of the audience, scope or algorithm the message was carrying.
 */
internal fun MessageSource.renderOrNull(
    messageId: String,
    locale: Locale,
    values: Map<String, Any>
): String? = getMessage(messageId, locale, values).orElse(null)

/**
 * Renders the message [messageId] names for a reader with no locale of their own — an operator
 * reading a log or a health check — and falls back to [messageId] itself when the bundle holds no
 * such message.
 */
internal fun MessageSource.render(messageId: String, values: Map<String, Any>): String =
    renderOrNull(messageId, DEFAULT_LOCALE, values) ?: messageId
