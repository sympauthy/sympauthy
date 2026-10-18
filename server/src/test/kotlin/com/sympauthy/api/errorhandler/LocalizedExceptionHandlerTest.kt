package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.InsufficientUserAuthenticationException
import com.sympauthy.api.exception.UnsupportedMethodException
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.mapper.ErrorResourceMapper
import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.exception.LocalizedException
import com.sympauthy.security.AccessTokenScheme
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
            InsufficientUserAuthenticationException(AccessTokenScheme.BEARER, Duration.ofMinutes(5))
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertEquals(
            """Bearer realm="sympauthy", error="insufficient_user_authentication", """ +
                """error_description="A more recent authentication is required", max_age="300", """ +
                """DPoP realm="sympauthy", algs="RS256 RS384 RS512 ES256 ES384 ES512 PS256 PS384 PS512"""",
            response.header(HttpHeaders.WWW_AUTHENTICATE)
        )
    }

    @Test
    fun `handle - Answer a refused method with the Allow header`() {
        stubResource(HttpStatus.METHOD_NOT_ALLOWED)

        val response = handler.handle(request(), UnsupportedMethodException("PUT", listOf("GET", "POST")))

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.status)
        assertEquals("GET, POST", response.header(HttpHeaders.ALLOW))
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
