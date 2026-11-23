package com.sympauthy.platform.exception

import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR

/**
 * A failure of a mechanism the server rests on, which is always the server's own: nothing the caller sent
 * would have prevented it, so it is answered as an internal business failure is, with a `500` and no
 * description. See `docs/standards/exception-code-standard.md`.
 *
 * Note: use [platformExceptionOf] to create instances of this class.
 */
class PlatformException(
    detailsId: String,
    values: Map<String, String> = emptyMap(),
    throwable: Throwable? = null
) : LocalizedException(
    recoverable = false,
    detailsId = detailsId,
    descriptionId = null,
    values = values,
    throwable = throwable
) {
    override val answeredStatus: HttpStatus get() = INTERNAL_SERVER_ERROR
}

/**
 * Factory method to create a [PlatformException].
 *
 * Note: this method should always be preferred over the constructor for creating instances as it provides a convenient
 * vararg for [values].
 */
fun platformExceptionOf(
    detailsId: String,
    vararg values: Pair<String, String>
) = PlatformException(
    detailsId = detailsId,
    values = mapOf(*values)
)
