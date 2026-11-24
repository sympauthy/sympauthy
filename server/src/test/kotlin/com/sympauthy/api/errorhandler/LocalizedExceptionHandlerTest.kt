package com.sympauthy.api.errorhandler

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sympauthy.api.ADMIN_SURFACE
import com.sympauthy.api.FLOW_SURFACE
import com.sympauthy.api.OPENID_SURFACE
import com.sympauthy.api.exception.AccessTokenChallengeException
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.UnsupportedMethodException
import com.sympauthy.api.exception.accessTokenChallengeExceptionOf
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.exception.recoverableHttpExceptionOf
import com.sympauthy.api.mapper.ErrorResourceMapper
import com.sympauthy.api.mapper.OAuth2ErrorResourceMapper
import com.sympauthy.api.protocol.oauth2.IntrospectionController.Companion.OAUTH2_INTROSPECTION_ENDPOINT
import com.sympauthy.api.protocol.oauth2.RevokeController.Companion.OAUTH2_REVOKE_ENDPOINT
import com.sympauthy.api.protocol.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.api.resource.error.OAuth2ErrorResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.oauth2.model.OAuth2ErrorCode.INVALID_CLIENT
import com.sympauthy.business.oauth2.model.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.oauth2.model.OAuth2ErrorCode.SERVER_ERROR
import com.sympauthy.business.token.model.WwwAuthenticateChallenge.InsufficientUserAuthentication
import com.sympauthy.business.token.model.WwwAuthenticateChallenge.InvalidToken
import com.sympauthy.config.model.Config
import com.sympauthy.config.model.invalidConfig
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.mapper.LocalizedErrorMapper
import com.sympauthy.platform.monitoring.manager.ConditionMonitor
import com.sympauthy.platform.monitoring.model.Condition
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
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.r2dbc.spi.R2dbcNonTransientResourceException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
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

    @SpyK
    var conditionMonitor = ConditionMonitor()

    @InjectMockKs
    lateinit var handler: LocalizedExceptionHandler

    private val written = ListAppender<ILoggingEvent>()

    private val logger = LoggerFactory.getLogger(LocalizedExceptionHandler::class.java) as ch.qos.logback.classic.Logger

    @BeforeEach
    fun attach() {
        written.start()
        logger.addAppender(written)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(written)
    }

    @Test
    fun `handle - Log a server failure at WARN with its code, its values and its cause`() {
        stubResource(HttpStatus.INTERNAL_SERVER_ERROR)
        val cause = IllegalArgumentException("No enum constant UserStatus.FOO")
        val exception = internalBusinessExceptionOf("mapper.user.invalid_property", cause, "property" to "status")

        val response = handler.handle(request(), exception)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status)
        val warning = written.list.single()
        assertEquals(Level.WARN, warning.level)
        assertEquals("Server failure mapper.user.invalid_property {property=status}", warning.formattedMessage)
        assertEquals(exception.message, warning.throwableProxy.message)
        assertEquals(cause.message, warning.throwableProxy.cause.message)
    }

    @Test
    fun `handle - Log an unhandled failure at ERROR as the throwable it was normalized from, asking for an issue`() {
        stubResource(HttpStatus.INTERNAL_SERVER_ERROR)
        val failure = IllegalStateException("Broken")

        handler.handle(request(), internalServerError(failure), failure)

        val error = written.list.single()
        assertEquals(Level.ERROR, error.level)
        assertEquals(
            "Unhandled failure internal_server_error: this is most likely a bug in SympAuthy. " +
                "Please open an issue at https://github.com/sympauthy/sympauthy/issues.",
            error.formattedMessage
        )
        assertEquals(IllegalStateException::class.java.name, error.throwableProxy.className)
    }

    @Test
    fun `handle - Report an unhandled failure caused by the database out of reach to its condition, not the log`() {
        stubResource(HttpStatus.INTERNAL_SERVER_ERROR)
        val failure = R2dbcNonTransientResourceException("Connection refused")

        handler.handle(request(), internalServerError(failure), failure)

        assertEquals(emptyList<ILoggingEvent>(), written.list)
        assertTrue(conditionMonitor.listObserved().getValue(Condition.Database))
    }

    @Test
    fun `handle - Log nothing for a configuration found invalid, which the startup verdict announced`() {
        stubResource(HttpStatus.INTERNAL_SERVER_ERROR)

        handler.handle(request(), Config().invalidConfig)

        assertEquals(emptyList<ILoggingEvent>(), written.list)
    }

    @Test
    fun `handle - Log nothing for a refusal answered with a 4xx`() {
        stubResource(HttpStatus.BAD_REQUEST)

        handler.handle(
            request(),
            recoverableHttpExceptionOf(HttpStatus.BAD_REQUEST, "body.malformed", "description.body.malformed")
        )

        assertEquals(emptyList<ILoggingEvent>(), written.list)
    }

    @Test
    fun `record - Log nothing for a failure rendering to a 4xx, whatever answered it`() {
        stubStatus(HttpStatus.BAD_REQUEST)
        val refusal = BusinessException(
            recoverable = false,
            detailsId = "flow.sign_up.disabled",
            recommendedStatus = HttpStatus.BAD_REQUEST
        )

        handler.record(refusal, refusal)

        assertEquals(emptyList<ILoggingEvent>(), written.list)
    }

    @Test
    fun `record - Log a server failure at WARN, whatever answered it`() {
        stubStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        val failure = internalBusinessExceptionOf("mapper.user.invalid_property", "property" to "status")

        handler.record(failure, failure)

        assertEquals(Level.WARN, written.list.single().level)
    }

    @Test
    fun `handle - Report a server failure caused by the database out of reach to its condition, not the log`() {
        stubResource(HttpStatus.INTERNAL_SERVER_ERROR)
        val exception = internalBusinessExceptionOf(
            "manager.failed",
            R2dbcNonTransientResourceException("Connection refused")
        )

        handler.handle(request(), exception)

        assertEquals(emptyList<ILoggingEvent>(), written.list)
        assertTrue(conditionMonitor.listObserved().getValue(Condition.Database))
    }

    @Test
    fun `handle - Answer a refusal carrying a challenge with the WWW-Authenticate header`() {
        stubResource(HttpStatus.UNAUTHORIZED)

        val response = handler.handle(
            requestToRouteSecuredBy(IS_USER),
            accessTokenChallengeExceptionOf(
                AccessTokenScheme.BEARER,
                listOf(InsufficientUserAuthentication(Duration.ofMinutes(5)))
            )
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
            request("$FLOW_SURFACE/sign-in"),
            recoverableHttpExceptionOf(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "content_type.unsupported", "description.content_type.unsupported"
            )
        )

        assertSame(resource, response.body())
    }

    private fun internalServerError(failure: Throwable) = httpExceptionOf(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", "description.internal_server_error", failure
    )

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

    private fun request(path: String = "$ADMIN_SURFACE/users") = mockk<HttpRequest<*>> {
        every { locale } returns Optional.of(Locale.ENGLISH)
        every { this@mockk.path } returns path
    }

    private fun requestToRouteSecuredBy(vararg roles: String): HttpRequest<*> {
        val request = HttpRequest.GET<Any>("$OPENID_SURFACE/userinfo")
        RouteAttributes.setRouteMatch(
            request,
            mockk<MethodBasedRouteMatch<Any, Any>> {
                every { getValue(Secured::class.java, Array<String>::class.java) } returns Optional.of(arrayOf(*roles))
            }
        )
        return request
    }

    private fun refusedTokenChallenge() = AccessTokenChallengeException(
        recoverable = false,
        detailsId = "jwt.malformed",
        descriptionId = "description.unauthorized",
        scheme = AccessTokenScheme.BEARER,
        challenges = listOf(InvalidToken)
    )
}
