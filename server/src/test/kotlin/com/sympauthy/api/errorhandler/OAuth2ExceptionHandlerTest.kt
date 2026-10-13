package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.mapper.OAuth2ErrorResourceMapper
import com.sympauthy.api.resource.error.OAuth2ErrorResource
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_CLIENT
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
import java.util.*

@ExtendWith(MockKExtension::class)
class OAuth2ExceptionHandlerTest {

    @MockK
    lateinit var oAuth2ErrorResourceMapper: OAuth2ErrorResourceMapper

    @InjectMockKs
    lateinit var handler: OAuth2ExceptionHandler

    @Test
    fun `handle - Answer an error carrying a challenge with 401 and the WWW-Authenticate header`() {
        stubResource()

        val response = handler.handle(
            request(),
            OAuth2Exception(
                errorCode = INVALID_CLIENT,
                detailsId = "authentication.wrong",
                challenge = """Basic realm="oauth2""""
            )
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertEquals("""Basic realm="oauth2"""", response.header(HttpHeaders.WWW_AUTHENTICATE))
    }

    @Test
    fun `handle - Answer an error carrying no challenge with its status and no WWW-Authenticate header`() {
        stubResource()

        val response = handler.handle(
            request(),
            oauth2ExceptionOf(INVALID_CLIENT, "authentication.missing_credentials")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertNull(response.header(HttpHeaders.WWW_AUTHENTICATE))
    }

    private fun stubResource() {
        every { oAuth2ErrorResourceMapper.toResource(any(), any()) } returns OAuth2ErrorResource(
            errorCode = "invalid_client"
        )
    }

    private fun request() = mockk<HttpRequest<*>> {
        every { locale } returns Optional.of(Locale.ENGLISH)
    }
}
