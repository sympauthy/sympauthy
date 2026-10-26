package com.sympauthy.client

import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import java.io.IOException
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
