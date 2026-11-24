package com.sympauthy.client

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.platform.monitoring.model.Outcome
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class RemoteFailureTest {

    private fun answered(status: HttpStatus) =
        HttpClientResponseException(status.reason, HttpResponse.status<Any>(status))

    private fun answered(code: Int) =
        HttpClientResponseException("Answered $code", HttpResponse.status<Any>(code, "Answered $code"))

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

    @Test
    fun `isRemoteFailure - Is true for a 5xx HttpStatus does not name`() {
        assertTrue(answered(520).isRemoteFailure())
    }

    @Test
    fun `remoteOutcome - Types a status HttpStatus does not name by its code`() {
        assertEquals(Outcome.Error("520"), answered(520).remoteOutcome())
        assertEquals(Outcome.Refused("499"), answered(499).remoteOutcome())
    }

    @Test
    fun `remoteResponseOrNull - Finds the answer however deep the cause, and none where nothing answered`() {
        val response = answered(HttpStatus.BAD_REQUEST)

        assertSame(response, IllegalStateException("wrapped", response).remoteResponseOrNull())
        assertNull(ReadTimeoutException.TIMEOUT_EXCEPTION.remoteResponseOrNull())
    }

    @Test
    fun `remoteOutcome - Is an error typed by the status for a 5xx`() {
        assertEquals(Outcome.Error("503"), answered(HttpStatus.SERVICE_UNAVAILABLE).remoteOutcome())
    }

    @Test
    fun `remoteOutcome - Is a refusal typed by the status for a 4xx`() {
        assertEquals(Outcome.Refused("400"), answered(HttpStatus.BAD_REQUEST).remoteOutcome())
    }

    @Test
    fun `remoteOutcome - Is a refusal typed by its code for a failure after the answer was read`() {
        val failure = businessExceptionOf("provider.openid_connect.invalid_id_token")

        assertEquals(Outcome.Refused("provider.openid_connect.invalid_id_token"), failure.remoteOutcome())
    }

    @Test
    fun `remoteErrorType - Tells a timeout from a server that cannot be reached`() {
        assertEquals("timeout", ReadTimeoutException.TIMEOUT_EXCEPTION.remoteErrorType())
        assertEquals("timeout", IllegalStateException("wrapped", SocketTimeoutException()).remoteErrorType())
        assertEquals("connection", ConnectException("Connection refused").remoteErrorType())
    }

    @Test
    fun `remoteErrorType - Types a timeout a connection failure wraps as the timeout`() {
        assertEquals("timeout", IOException("Read failed", SocketTimeoutException()).remoteErrorType())
    }
}
