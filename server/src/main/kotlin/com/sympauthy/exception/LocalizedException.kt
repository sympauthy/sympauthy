package com.sympauthy.exception

import io.micronaut.http.HttpStatus

/**
 * Base class for exceptions where the message is localized in a resource bundle.
 */
open class LocalizedException(
    /**
     * Whether the error is caused by a user action or a system issue.
     * If recoverable, the user can take action to resolve the issue.
     */
    override val recoverable: Boolean = false,
    /**
     * Identifier of the message detailing the issue in a technical way.
     *
     * It is mostly meant for developers and administrators to troubleshoot the issue.
     *
     * This is also used as an identifier for the error reported to the user.
     */
    override val detailsId: String,
    /**
     * Identifier of the message displayed to the end user, or null where the failure has nothing of its own to tell
     * them. See `docs/standards/exception-code-standard.md`.
     */
    val descriptionId: String? = null,
    /**
     * Values interpolated into the `{name}` placeholders of both localized messages.
     */
    val values: Map<String, String> = emptyMap(),
    /**
     * Underlying cause of this exception.
     */
    val throwable: Throwable? = null
) : Exception(formatMessage(detailsId, values), throwable), AnsweredFailure {

    override val answeredStatus: HttpStatus? get() = null

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
