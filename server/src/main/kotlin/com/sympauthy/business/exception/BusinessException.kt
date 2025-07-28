package com.sympauthy.business.exception

import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpStatus

/**
 * Exception describing an error that will be exposed to the end-user.
 * Ex. if the user provided an identifier that does not exist in the system.
 *
 * The description must be localized and comprehensible by the user to allow him to solve the issue.
 */
open class BusinessException(
    detailsId: String,
    descriptionId: String? = null,
    values: Map<String, Any?> = emptyMap(),
    val recommendedStatus: HttpStatus? = null,
    throwable: Throwable? = null
) : LocalizedException(
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    throwable
)

fun businessExceptionOf(
    detailsId: String,
    descriptionId: String? = null,
    recommendedStatus: HttpStatus? = null,
    vararg values: Pair<String, Any?>
): BusinessException = BusinessException(
    detailsId = detailsId,
    descriptionId = descriptionId,
    recommendedStatus = recommendedStatus,
    values = mapOf(*values)
)
