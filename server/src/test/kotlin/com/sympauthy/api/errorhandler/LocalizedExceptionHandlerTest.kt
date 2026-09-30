package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.InsufficientUserAuthenticationException
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.mapper.ErrorResourceMapper
import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.*

@ExtendWith(MockKExtension::class)
class LocalizedExceptionHandlerTest {

    @MockK
    lateinit var errorResourceMapper: ErrorResourceMapper

    @InjectMockKs
    lateinit var handler: LocalizedExceptionHandler

    @Test
    fun `handle - Answer a refusal carrying a challenge with the WWW-Authenticate header`() {
        stubResource(HttpStatus.UNAUTHORIZED)

        val response = handler.handle(
            request(),
            InsufficientUserAuthenticationException(Duration.ofMinutes(5))
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertEquals(
            """Bearer error="insufficient_user_authentication", """ +
                """error_description="A more recent authentication is required", max_age="300"""",
            response.header(HttpHeaders.WWW_AUTHENTICATE)
        )
    }

    @Test
    fun `handle - Answer a refusal carrying no challenge with no WWW-Authenticate header`() {
        stubResource(HttpStatus.NOT_FOUND)

        val response = handler.handle(
            request(),
            httpExceptionOf(HttpStatus.NOT_FOUND, "user.not_found", "description.not_found")
        )

        assertNull(response.header(HttpHeaders.WWW_AUTHENTICATE))
    }

    private fun stubResource(status: HttpStatus) {
        every { errorResourceMapper.toResource(any<LocalizedException>(), any()) } returns ErrorResource(
            status = status.code,
            errorCode = "code",
            description = null,
            details = null,
            properties = null
        )
    }

    private fun request() = mockk<HttpRequest<*>> {
        every { locale } returns Optional.of(Locale.ENGLISH)
    }
}
