package com.sympauthy.business.exception

import io.micronaut.http.HttpStatus.BAD_REQUEST

/**
 * A token this server was asked to verify is not one it will accept: it does not parse, it was not
 * signed by the key it names, or it has expired. The [detailsId] says which — `jwt.malformed`,
 * `jwt.invalid_signature` or `jwt.expired`.
 *
 * It is a type rather than a list of codes because a caller catching a token failure must not also
 * catch this server failing to load its own signing key. Both travel out of the same call, and only
 * this one is the caller's fault.
 */
class InvalidJwtException(
    detailsId: String,
    throwable: Throwable? = null
) : BusinessException(
    recoverable = false,
    detailsId = detailsId,
    recommendedStatus = BAD_REQUEST,
    throwable = throwable
)
