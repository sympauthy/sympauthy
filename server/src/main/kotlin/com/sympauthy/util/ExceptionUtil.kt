package com.sympauthy.util

import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.exception.LocalizedException
import io.micronaut.context.MessageSource
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
 * Renders the message [messageId] names, interpolating [values] into its placeholders, and falls back
 * to [messageId] itself when the bundle holds no such message.
 *
 * [values] may not admit a null, and the type saying so is what makes this work. The message source
 * takes named values and positional values through two functions of the same name, and a map whose
 * values are nullable matches only the positional one — so the call binds there, silently, and the
 * message is rendered with no named value in scope at all. Every placeholder in it then resolves to
 * nothing and is written out as its own name, which is what an operator reads instead of the audience,
 * scope or algorithm the message was carrying.
 */
private fun MessageSource.render(messageId: String, values: Map<String, Any>): String =
    getMessage(messageId, Locale.US, values).orElse(messageId)
