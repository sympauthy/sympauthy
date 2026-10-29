package com.sympauthy.client

import com.sympauthy.exception.LocalizedException
import com.sympauthy.monitoring.telemetry.Outcome
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Whether this failure of a call to another server says that server is unwell: it could not be reached,
 * it did not answer in time, or it answered with a `5xx`.
 *
 * A `4xx` is not, because it answers what was sent: a provider refusing a code somebody replayed says
 * nothing about the provider. Nor is anything thrown after the answer was read.
 */
fun Throwable.isRemoteFailure(): Boolean {
    for (failure in generateSequence(this) { it.cause }) {
        when (failure) {
            is HttpClientResponseException -> return failure.status.code >= 500
            is HttpClientException, is IOException, is TimeoutException -> return true
        }
    }
    return false
}

/**
 * How a call to another server that ended in this failure is recorded: an [Outcome.Error] where
 * [isRemoteFailure] holds, typed by [remoteErrorType], and an [Outcome.Refused] otherwise — a `4xx` typed by its
 * status, anything thrown after the answer was read by its error code, or by its class where it carries none.
 */
fun Throwable.remoteOutcome(): Outcome {
    if (isRemoteFailure()) return Outcome.Error(remoteErrorType())
    val response = generateSequence(this) { it.cause }.filterIsInstance<HttpClientResponseException>().firstOrNull()
    val errorType = response?.status?.code?.toString() ?: (this as? LocalizedException)?.detailsId ?: javaClass.name
    return Outcome.Refused(errorType)
}

/**
 * What a failed call to another server is typed by on a metric: the status it answered with, `timeout` where
 * it did not answer in time, `connection` where it could not be reached or the connection broke, and the class
 * of the failure where it is none of these.
 */
fun Throwable.remoteErrorType(): String {
    for (failure in generateSequence(this) { it.cause }) {
        when (failure) {
            is HttpClientResponseException -> return failure.status.code.toString()
            is ReadTimeoutException, is TimeoutException, is SocketTimeoutException,
            is TimeoutCancellationException -> return TIMEOUT
            is HttpClientException, is IOException -> return CONNECTION
        }
    }
    return javaClass.name
}

private const val TIMEOUT = "timeout"
private const val CONNECTION = "connection"
