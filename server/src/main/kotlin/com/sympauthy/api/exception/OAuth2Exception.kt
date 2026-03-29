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

fun OAuth2Exception.toHttpException(
    httpStatus: HttpStatus = status
) = LocalizedHttpException(
    status = httpStatus,
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
