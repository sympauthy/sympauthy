package com.sympauthy.client

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ConnectException

class RemoteFailureTest {

    private fun answered(status: HttpStatus) =
        HttpClientResponseException(status.reason, HttpResponse.status<Any>(status))

    @Test
    fun `isRemoteFailure - Is true for a 5xx`() {
        assertTrue(answered(HttpStatus.SERVICE_UNAVAILABLE).isRemoteFailure())
    }

    @Test
    fun `isRemoteFailure - Is false for a 4xx, which answers what was sent`() {
        assertFalse(answered(HttpStatus.BAD_REQUEST).isRemoteFailure())
    }

    @Test
    fun `isRemoteFailure - Is true for a server that cannot be reached, however deep the cause`() {
        assertTrue(IllegalStateException("wrapped", ConnectException("Connection refused")).isRemoteFailure())
        assertTrue(HttpClientException("Read Timeout").isRemoteFailure())
    }

    @Test
    fun `isRemoteFailure - Is false for a failure that is not a call's`() {
        assertFalse(IllegalArgumentException("invalid token").isRemoteFailure())
    }
}
