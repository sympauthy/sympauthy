package com.sympauthy.api.exception

import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpStatus

/**
 * Base class for exceptions that will cause this server to return
 */
open class LocalizedHttpException(
    val status: HttpStatus,
    detailsId: String,
    descriptionId: String? = null,
    values: Map<String, String> = emptyMap(),
    val additionalInfo: List<LocalizedAdditionalMessage> = emptyList(),
    throwable: Throwable? = null
) : LocalizedException(
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    throwable = throwable
)

/**
 * Additional information about the error that is related to:
 * - a query param.
 * - a property in the payload.
 */
data class LocalizedAdditionalMessage(
    /**
     * Path to a property of the payload causing the error.
     */
    val path: String? = null,
    /**
     * Query param causing the error.
     */
    val queryParam: String? = null,
    /**
     * Identifier of the message displayed to the end user.
     * It is intended to be displayed to the end-user, in order for him to correct its input and retry the operation.
     */
    val descriptionId: String? = null,
    val values: Map<String, Any> = emptyMap()
)

fun <T : LocalizedException> T.toHttpException(httpStatus: HttpStatus) = LocalizedHttpException(
    status = httpStatus,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    throwable = throwable
)

fun httpExceptionOf(
    status: HttpStatus,
    detailsId: String,
    vararg values: Pair<String, String>
): LocalizedHttpException = LocalizedHttpException(
    status = status,
    detailsId = detailsId,
    values = mapOf(*values)
)

fun httpExceptionOf(
    status: HttpStatus,
    detailsId: String,
    descriptionId: String?,
    vararg values: Pair<String, String>
): LocalizedHttpException = LocalizedHttpException(
    status = status,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = mapOf(*values)
)

fun httpExceptionOf(
    status: HttpStatus,
    detailsId: String,
    descriptionId: String?,
    throwable: Throwable?,
    vararg values: Pair<String, String>
): LocalizedHttpException = LocalizedHttpException(
    status = status,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = mapOf(*values),
    throwable = throwable
)
