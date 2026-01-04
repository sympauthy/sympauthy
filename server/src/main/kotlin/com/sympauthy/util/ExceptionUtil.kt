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
            val localizedErrorMessage = messageSource.getMessage(detailsId, Locale.US, values)
                .orElse(detailsId)
            key to localizedErrorMessage
        }

        is ConfigurationException -> {
            val localizedErrorMessage = messageSource.getMessage(messageId, Locale.US, values)
                .orElse(messageId)
            key to localizedErrorMessage
        }

        else -> javaClass.name to message
    }
}
