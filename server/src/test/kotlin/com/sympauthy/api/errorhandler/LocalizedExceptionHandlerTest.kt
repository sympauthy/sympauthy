package com.sympauthy.api.errorhandler

import com.sympauthy.api.controller.oauth2.IntrospectionController.Companion.OAUTH2_INTROSPECTION_ENDPOINT
import com.sympauthy.api.controller.oauth2.RevokeController.Companion.OAUTH2_REVOKE_ENDPOINT
import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.exception.InsufficientUserAuthenticationException
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.UnsupportedMethodException
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.exception.recoverableHttpExceptionOf
import com.sympauthy.api.exception.toAccessTokenChallengeException
import com.sympauthy.api.mapper.ErrorResourceMapper
import com.sympauthy.api.mapper.OAuth2ErrorResourceMapper
import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.api.resource.error.OAuth2ErrorResource
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_CLIENT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TOKEN
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.SERVER_ERROR
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.mapper.LocalizedErrorMapper
import com.sympauthy.security.AccessTokenScheme
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.SecurityRule.IS_USER
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.security.annotation.Secured
import io.micronaut.web.router.MethodBasedRouteMatch
import io.micronaut.web.router.RouteAttributes
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.*

@ExtendWith(MockKExtension::class)
class LocalizedExceptionHandlerTest {

    @MockK
    lateinit var localizedErrorMapper: LocalizedErrorMapper

    @MockK
    lateinit var errorResourceMapper: ErrorResourceMapper

    @MockK
    lateinit var oAuth2ErrorResourceMapper: OAuth2ErrorResourceMapper

    @InjectMockKs
    lateinit var handler: LocalizedExceptionHandler

    @Test
    fun `handle - Answer a refusal carrying a challenge with the WWW-Authenticate header`() {
        stubResource(HttpStatus.UNAUTHORIZED)

        val response = handler.handle(
            requestToRouteSecuredBy(IS_USER),
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
    fun `handle - Answer a challenge with no WWW-Authenticate where no route answers the request`() {
        stubResource(HttpStatus.UNAUTHORIZED)

        val response = handler.handle(HttpRequest.GET<Any>("/nope"), refusedTokenChallenge())

        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertNull(response.header(HttpHeaders.WWW_AUTHENTICATE))
    }

    @Test
    fun `handle - Answer a challenge with no WWW-Authenticate on a route gated on the state`() {
        stubResource(HttpStatus.UNAUTHORIZED)

        val response = handler.handle(requestToRouteSecuredBy(HAS_STATE), refusedTokenChallenge())

        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertNull(response.header(HttpHeaders.WWW_AUTHENTICATE))
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

    @Test
    fun `handle - Answer a refusal on the token endpoint with invalid_request in the RFC 6749 body`() {
        stubStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        val oAuth2Exception = slot<OAuth2Exception>()
        val oAuth2Resource = OAuth2ErrorResource(errorCode = "invalid_request")
        every { oAuth2ErrorResourceMapper.toResource(capture(oAuth2Exception), any()) } returns oAuth2Resource

        val response = handler.handle(
            request(OAUTH2_TOKEN_ENDPOINT),
            recoverableHttpExceptionOf(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "content_type.unsupported", "description.content_type.unsupported",
                "contentType" to "application/json"
            )
        )

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.status)
        assertSame(oAuth2Resource, response.body())
        assertEquals(INVALID_REQUEST, oAuth2Exception.captured.errorCode)
        assertEquals("content_type.unsupported", oAuth2Exception.captured.detailsId)
        assertEquals("description.content_type.unsupported", oAuth2Exception.captured.descriptionId)
        assertEquals(mapOf("contentType" to "application/json"), oAuth2Exception.captured.values)
    }

    @Test
    fun `handle - Answer a refusal on the token endpoint addressed with a trailing slash in the RFC 6749 body`() {
        stubStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        val oAuth2Resource = OAuth2ErrorResource(errorCode = "invalid_request")
        every { oAuth2ErrorResourceMapper.toResource(any(), any()) } returns oAuth2Resource

        val response = handler.handle(
            request("$OAUTH2_TOKEN_ENDPOINT/"),
            recoverableHttpExceptionOf(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "content_type.unsupported", "description.content_type.unsupported"
            )
        )

        assertSame(oAuth2Resource, response.body())
    }

    @Test
    fun `handle - Answer an unauthenticated client on the token endpoint with invalid_client in the RFC 6749 body`() {
        stubStatus(HttpStatus.UNAUTHORIZED)
        val oAuth2Exception = slot<OAuth2Exception>()
        val oAuth2Resource = OAuth2ErrorResource(errorCode = "invalid_client")
        every { oAuth2ErrorResourceMapper.toResource(capture(oAuth2Exception), any()) } returns oAuth2Resource

        val response = handler.handle(
            request(OAUTH2_TOKEN_ENDPOINT),
            httpExceptionOf(HttpStatus.UNAUTHORIZED, "unauthorized", "description.unauthorized")
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertEquals(INVALID_CLIENT, oAuth2Exception.captured.errorCode)
    }

    @Test
    fun `handle - Answer a refused method on the revocation endpoint with the Allow header and the RFC 6749 body`() {
        stubStatus(HttpStatus.METHOD_NOT_ALLOWED)
        val oAuth2Resource = OAuth2ErrorResource(errorCode = "invalid_request")
        every { oAuth2ErrorResourceMapper.toResource(any(), any()) } returns oAuth2Resource

        val response = handler.handle(
            request(OAUTH2_REVOKE_ENDPOINT),
            UnsupportedMethodException("GET", listOf("POST"))
        )

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.status)
        assertEquals("POST", response.header(HttpHeaders.ALLOW))
        assertSame(oAuth2Resource, response.body())
    }

    @Test
    fun `handle - Answer a server failure on the introspection endpoint with server_error in the RFC 6749 body`() {
        stubStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        val oAuth2Exception = slot<OAuth2Exception>()
        val oAuth2Resource = OAuth2ErrorResource(errorCode = "server_error")
        every { oAuth2ErrorResourceMapper.toResource(capture(oAuth2Exception), any()) } returns oAuth2Resource

        val response = handler.handle(
            request(OAUTH2_INTROSPECTION_ENDPOINT),
            httpExceptionOf(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", "description.internal_server_error"
            )
        )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status)
        assertSame(oAuth2Resource, response.body())
        assertEquals(SERVER_ERROR, oAuth2Exception.captured.errorCode)
    }

    @Test
    fun `handle - Answer a refusal on any other endpoint with the ordinary error body`() {
        val resource = stubResource(HttpStatus.UNSUPPORTED_MEDIA_TYPE)

        val response = handler.handle(
            request("/api/v1/flow/sign-in"),
            recoverableHttpExceptionOf(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "content_type.unsupported", "description.content_type.unsupported"
            )
        )

        assertSame(resource, response.body())
    }

    private fun stubStatus(status: HttpStatus) {
        every { localizedErrorMapper.toHttpStatus(any()) } returns status
    }

    private fun stubResource(status: HttpStatus): ErrorResource {
        stubStatus(status)
        val resource = ErrorResource(
            status = status.code,
            errorCode = "code",
            description = null,
            details = null,
            properties = null
        )
        every { errorResourceMapper.toResource(any<LocalizedException>(), any()) } returns resource
        return resource
    }

    private fun request(path: String = "/api/v1/admin/users") = mockk<HttpRequest<*>> {
        every { locale } returns Optional.of(Locale.ENGLISH)
        every { this@mockk.path } returns path
    }

    private fun requestToRouteSecuredBy(vararg roles: String): HttpRequest<*> {
        val request = HttpRequest.GET<Any>("/api/openid/userinfo")
        RouteAttributes.setRouteMatch(
            request,
            mockk<MethodBasedRouteMatch<Any, Any>> {
                every { getValue(Secured::class.java, Array<String>::class.java) } returns Optional.of(arrayOf(*roles))
            }
        )
        return request
    }

    private fun refusedTokenChallenge() = httpExceptionOf(
        HttpStatus.UNAUTHORIZED, "jwt.malformed", "description.unauthorized"
    ).toAccessTokenChallengeException(AccessTokenScheme.BEARER, INVALID_TOKEN)
}
