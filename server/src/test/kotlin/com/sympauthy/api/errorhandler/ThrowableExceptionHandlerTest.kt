package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.httpExceptionOf
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ThrowableExceptionHandlerTest {

    private val exceptionNormalizer = mockk<ExceptionNormalizer>()

    private val exceptionHandler = mockk<LocalizedExceptionHandler>()

    private val handler = ThrowableExceptionHandler<Throwable>(exceptionNormalizer, exceptionHandler)

    @Test
    fun `handle - Hand the throwable over as the failure to record, beside the form it is answered in`() {
        val request = HttpRequest.GET<Any>("/")
        val failure = IllegalStateException("Broken")
        val normalized = httpExceptionOf(
            HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", "description.internal_server_error", failure
        )
        val response = HttpResponse.serverError<Any>()
        every { exceptionNormalizer.normalize(failure) } returns normalized
        every { exceptionHandler.handle(request, normalized, failure) } returns response

        assertSame(response, handler.handle(request, failure))
    }
}
