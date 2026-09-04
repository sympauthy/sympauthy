package com.sympauthy.api.exception

import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpStatus

/**
 * Base class for exceptions that will cause this server to respond with [status] instead of the
 * status the root type's [recoverable] flag would have chosen.
 *
 * A failure the `api` layer raises names both of its messages, since it is the layer that knows
 * which caller is reading. The factories below take the description for that reason, and the one
 * null [descriptionId] left is a business failure converted by [toHttpException].
 *
 * Note: one of those factories should be preferred over this constructor, which is for a failure
 * carrying more than a code and its values — the [propertyErrors] of a payload, or the codes and
 * values another result is being forwarded under.
 */
open class LocalizedHttpException(
    val status: HttpStatus,
    recoverable: Boolean,
    detailsId: String,
    descriptionId: String? = null,
    values: Map<String, String> = emptyMap(),
    /**
     * The failure of each property of the payload this exception refuses, by the path to that
     * property.
     *
     * An entry is the exception the property failed with, and is rendered as the error itself is:
     * it carries its own code, its own description and the values both of them interpolate. It is
     * the only place a single response reports more than one failure.
     *
     * A path is a wire path, which is why this is the `api` layer's exception and not the root one:
     * a manager refusing a value does not know what the payload called it.
     */
    val propertyErrors: Map<String, LocalizedException> = emptyMap(),
    throwable: Throwable? = null
) : LocalizedException(
    recoverable = recoverable,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    throwable = throwable
)

/**
 * This failure as a [LocalizedHttpException] answering with [httpStatus], carrying the code, the
 * description, the values and the recoverability it already holds.
 *
 * It is how a failure raised below the `api` layer reaches a caller, and the one way a
 * [LocalizedHttpException] ends up naming no description: a non-recoverable business failure has
 * none to forward.
 */
fun <T : LocalizedException> T.toHttpException(httpStatus: HttpStatus) = LocalizedHttpException(
    status = httpStatus,
    recoverable = recoverable,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    throwable = throwable
)

/**
 * Factory method to create an exception answering with [status] that the caller cannot recover from
 * by sending something else — the row is not there, or the credential is of the wrong kind.
 *
 * Note: this method should always be preferred over the constructor for creating instances as it
 * provides a convenient vararg for the values [detailsId] and [descriptionId] interpolate.
 */
fun httpExceptionOf(
    status: HttpStatus,
    detailsId: String,
    descriptionId: String,
    vararg values: Pair<String, String>
): LocalizedHttpException = LocalizedHttpException(
    status = status,
    recoverable = false,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = mapOf(*values)
)

/**
 * Factory method to create an exception answering with [status] that the caller cannot recover from,
 * carrying the [throwable] that caused it.
 */
fun httpExceptionOf(
    status: HttpStatus,
    detailsId: String,
    descriptionId: String,
    throwable: Throwable?,
    vararg values: Pair<String, String>
): LocalizedHttpException = LocalizedHttpException(
    status = status,
    recoverable = false,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = mapOf(*values),
    throwable = throwable
)

/**
 * Factory method to create an exception answering with [status] that the caller can recover from by
 * sending something else — a page inside the bounds, a filter value the set holds.
 */
fun recoverableHttpExceptionOf(
    status: HttpStatus,
    detailsId: String,
    descriptionId: String,
    vararg values: Pair<String, String>
): LocalizedHttpException = LocalizedHttpException(
    status = status,
    recoverable = true,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = mapOf(*values)
)
