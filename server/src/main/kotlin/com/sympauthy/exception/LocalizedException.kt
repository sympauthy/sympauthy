package com.sympauthy.exception

/**
 * Base class for exceptions where the message is localized in a resource bundle.
 */
open class LocalizedException(
    /**
     * Whether the error is caused by a user action or a system issue.
     * If recoverable, the user can take action to resolve the issue.
     */
    val recoverable: Boolean = false,
    /**
     * Identifier of the message detailing the issue in a technical way.
     *
     * It is mostly meant for developers and administrators to troubleshoot the issue.
     *
     * This is also used as an identifier for the error reported to the user.
     */
    val detailsId: String,
    /**
     * Identifier of the message displayed to the end user.
     *
     * This message is meant to be displayed to the end user. It must help the user to understand what went wrong and how
     * to get out of the situation if the error is [recoverable].
     */
    val descriptionId: String? = null,
    /**
     * Value to expose to the mustache template to inject values into the localized message.
     */
    val values: Map<String, String> = emptyMap(),
    /**
     * Underlying cause of this exception.
     */
    val throwable: Throwable? = null
) : Exception(formatMessage(detailsId, values), throwable) {
    companion object {
        private fun formatMessage(detailsId: String, values: Map<String, String>): String {
            return if (values.isEmpty()) {
                detailsId
            } else {
                "$detailsId: $values"
            }
        }
    }
}

fun localizedExceptionOf(
    detailsId: String,
    vararg values: Pair<String, String>
) = LocalizedException(
    recoverable = false,
    detailsId = detailsId,
    values = mapOf(*values)
)
