package com.sympauthy.business.exception

import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpStatus.BAD_REQUEST
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR

/**
 * Exception describing an error that will be exposed to the end-user.
 * Ex. if the user provided an identifier that does not exist in the system.
 *
 * The description must be localized and comprehensible by the user to allow him to solve the issue
 * if the error is recoverable.
 *
 * Note: Use the [businessExceptionOf] or [recoverableBusinessExceptionOf] factory methods
 * to create instances of this class.
 */
open class BusinessException(
    recoverable: Boolean,
    detailsId: String,
    descriptionId: String? = null,
    values: Map<String, String> = emptyMap(),
    val recommendedStatus: HttpStatus? = null,
    throwable: Throwable? = null
) : LocalizedException(
    recoverable = recoverable,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    throwable
)

/**
 * Factory method to create a business exception that cannot be recovered by an end-user action
 * (ex. an expired authorization session).
 *
 * Note: this method should always be preferred over the constructor for creating instances as it provides a convenient
 * vararg for [values].
 */
fun businessExceptionOf(
    detailsId: String,
    descriptionId: String? = null,
    vararg values: Pair<String, String>
): BusinessException = BusinessException(
    recoverable = false,
    detailsId = detailsId,
    descriptionId = descriptionId,
    recommendedStatus = BAD_REQUEST,
    values = mapOf(*values)
)

/**
 * Factory method to create a business exception that can be recovered by an end-user action.
 *
 * Note: this method should always be preferred over the constructor for creating instances as it provides a convenient
 * vararg for [values].
 */
fun recoverableBusinessExceptionOf(
    detailsId: String,
    descriptionId: String? = null,
    vararg values: Pair<String, String>
) = BusinessException(
    recoverable = true,
    detailsId = detailsId,
    descriptionId = descriptionId,
    recommendedStatus = BAD_REQUEST,
    values = mapOf(*values)
)

/**
 * Factory method to create a non-recoverable [BusinessException] not caused by the end-user.
 */
fun internalBusinessExceptionOf(
    detailsId: String,
    vararg values: Pair<String, String>
) = BusinessException(
    recoverable = false,
    detailsId = detailsId,
    descriptionId = null,
    recommendedStatus = INTERNAL_SERVER_ERROR,
    values = mapOf(*values)
)
