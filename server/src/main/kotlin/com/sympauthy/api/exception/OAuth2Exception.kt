package com.sympauthy.api.exception

import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge
import com.sympauthy.exception.AnsweredFailure
import com.sympauthy.exception.LocalizedException
import com.sympauthy.security.AccessTokenScheme
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpStatus.UNAUTHORIZED

class OAuth2Exception(
    val errorCode: OAuth2ErrorCode,
    override val detailsId: String,
    val descriptionId: String? = null,
    val values: Map<String, String> = emptyMap(),
    /**
     * The `WWW-Authenticate` value this error answers with, or null where it asks the client for no
     * credential.
     *
     * An error carrying one answers `401` whatever [errorCode] says, because that is the status a
     * challenge is sent with, RFC 9110 §15.5.2.
     */
    val challenge: String? = null
) : Exception(formatMessage(errorCode, detailsId)), AnsweredFailure {

    val status: HttpStatus = if (challenge != null) UNAUTHORIZED else errorCode.status

    /**
     * Never: the protocol's error body carries no such flag, and a client branches on [errorCode].
     */
    override val recoverable: Boolean get() = false

    override val answeredStatus: HttpStatus get() = status

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

/**
 * This protocol error as an [AccessTokenChallengeException] challenging the token presented under [scheme] with
 * [challenges], which may name another error than [OAuth2Exception.errorCode]: the code a manager names is the
 * one the token endpoint answers with.
 *
 * It is not recoverable, for the reason [toHttpException] is not.
 */
fun OAuth2Exception.toAccessTokenChallengeException(
    scheme: AccessTokenScheme,
    challenges: List<WwwAuthenticateChallenge>
) = AccessTokenChallengeException(
    recoverable = false,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    scheme = scheme,
    challenges = challenges
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
