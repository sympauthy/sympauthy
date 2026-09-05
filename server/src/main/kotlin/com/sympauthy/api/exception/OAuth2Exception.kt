package com.sympauthy.api.exception

import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpStatus

class OAuth2Exception(
    val errorCode: OAuth2ErrorCode,
    val detailsId: String,
    val descriptionId: String? = null,
    val values: Map<String, String> = emptyMap(),
) : Exception(formatMessage(errorCode, detailsId)) {

    val status: HttpStatus = errorCode.status

    companion object {
        private fun formatMessage(errorCode: OAuth2ErrorCode, detailsId: String?): String {
            return "OAuth2 - ${errorCode.errorCode} - $detailsId"
        }
    }
}

/**
 * This protocol error as a [LocalizedHttpException] answering with [httpStatus], for the callers
 * that answer with the ordinary error body rather than with the one RFC 6749 defines.
 *
 * It is not recoverable: the protocol's error body carries no such flag, so there is nothing to
 * forward, and the code an OAuth2 client branches on is [OAuth2Exception.errorCode] either way.
 */
fun OAuth2Exception.toHttpException(
    httpStatus: HttpStatus = status
) = LocalizedHttpException(
    status = httpStatus,
    recoverable = false,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values
)

fun LocalizedException.toOAuth2Exception(
    errorCode: OAuth2ErrorCode,
    descriptionId: String? = null
) = OAuth2Exception(
    errorCode = errorCode,
    detailsId = detailsId,
    descriptionId = descriptionId ?: this.descriptionId,
    values = values
)

fun oauth2ExceptionOf(
    errorCode: OAuth2ErrorCode,
    detailsId: String,
    vararg values: Pair<String, String>
) = OAuth2Exception(errorCode, detailsId, null, mapOf(*values))

fun oauth2ExceptionOf(
    errorCode: OAuth2ErrorCode,
    detailsId: String,
    descriptionId: String,
    vararg values: Pair<String, String>
) = OAuth2Exception(errorCode, detailsId, descriptionId, mapOf(*values))
