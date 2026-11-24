package com.sympauthy.client

import com.sympauthy.exception.LocalizedException
import com.sympauthy.platform.monitoring.model.Outcome
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Whether this failure of a call to another server says that server is unwell: it could not be reached,
 * it did not answer in time, or it answered with a `5xx`. A `4xx` is not, nor is anything thrown after the
 * answer was read — see `docs/design/logging.md`, which also names the callers counting a `4xx` anyway.
 */
fun Throwable.isRemoteFailure(): Boolean {
    for (failure in generateSequence(this) { it.cause }) {
        when (failure) {
            is HttpClientResponseException -> return failure.code() >= 500
            is HttpClientException, is IOException, is TimeoutException -> return true
        }
    }
    return false
}

/**
 * The answer the other server gave along this failure's chain, or null where it gave none.
 *
 * Its status is read with `code()`: `getStatus()` throws for a status [io.micronaut.http.HttpStatus] does not
 * name, which a CDN in front of a provider answers with.
 */
fun Throwable.remoteResponseOrNull(): HttpClientResponseException? =
    generateSequence(this) { it.cause }.filterIsInstance<HttpClientResponseException>().firstOrNull()

/**
 * How a call to another server that ended in this failure is recorded: an [Outcome.Error] where
 * [isRemoteFailure] holds, typed by [remoteErrorType], and an [Outcome.Refused] otherwise — a `4xx` typed by its
 * status, anything thrown after the answer was read by its error code, or by its class where it carries none.
 */
fun Throwable.remoteOutcome(): Outcome {
    if (isRemoteFailure()) return Outcome.Error(remoteErrorType())
    val errorType = remoteResponseOrNull()?.code()?.toString()
        ?: (this as? LocalizedException)?.detailsId
        ?: javaClass.name
    return Outcome.Refused(errorType)
}

/**
 * What a failed call to another server is typed by on a metric: the status it answered with, `timeout` where
 * it did not answer in time, `connection` where it could not be reached or the connection broke, and the class
 * of the failure where it is none of these.
 *
 * Each is looked for along the whole chain before the next, so that a timeout a connection failure wraps is
 * typed as the timeout it is.
 */
fun Throwable.remoteErrorType(): String {
    remoteResponseOrNull()?.let { return it.code().toString() }
    val chain = generateSequence(this) { it.cause }.toList()
    return when {
        chain.any {
            it is ReadTimeoutException || it is TimeoutException || it is SocketTimeoutException ||
                it is TimeoutCancellationException
        } -> TIMEOUT
        chain.any { it is HttpClientException || it is IOException } -> CONNECTION
        else -> javaClass.name
    }
}

private const val TIMEOUT = "timeout"
private const val CONNECTION = "connection"
