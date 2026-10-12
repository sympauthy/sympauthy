package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.UnsupportedMethodException
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException
import io.micronaut.core.convert.ConversionError
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpHeaders
import io.micronaut.http.annotation.Body
import io.micronaut.http.exceptions.BufferLengthExceededException
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.server.exceptions.NotAcceptableException
import io.micronaut.http.server.exceptions.NotAllowedException
import io.micronaut.http.server.exceptions.NotFoundException
import io.micronaut.http.server.exceptions.UnsupportedMediaException
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.json.JsonSyntaxException
import io.micronaut.security.authentication.AuthenticationException
import io.micronaut.web.router.exceptions.DuplicateRouteException
import io.micronaut.web.router.exceptions.UnsatisfiedBodyRouteException
import io.micronaut.web.router.exceptions.UnsatisfiedQueryValueRouteException
import io.micronaut.web.router.exceptions.UnsatisfiedRequestAttributeRouteException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.*

class ExceptionNormalizerTest {

    private val normalizer = ExceptionNormalizer()

    @Test
    fun `normalize - Answer a parameter that does not convert with parameter malformed`() {
        val exception = normalizer.normalize(
            conversionErrorOf(Argument.of(UUID::class.java, "userId"), originalValue = "not-a-uuid")
        )

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("parameter.malformed", exception.detailsId)
        assertEquals("description.parameter.malformed", exception.descriptionId)
        assertEquals(mapOf("parameter" to "userId", "value" to "not-a-uuid"), exception.values)
    }

    @Test
    fun `normalize - Name a parameter by the name the request carries it under`() {
        val argument = Argument.of(Int::class.java, "maxAge", annotationMetadataOf(Bindable::class.java, "max_age"))

        val exception = normalizer.normalize(conversionErrorOf(argument, originalValue = "abc"))

        assertEquals("max_age", exception.values["parameter"])
    }

    @Test
    fun `normalize - Pass an empty value where the framework kept none of the value sent`() {
        val exception = normalizer.normalize(
            ConversionErrorException(Argument.of(Int::class.java, "page"), NumberFormatException())
        )

        assertEquals("", exception.values["value"])
    }

    @Test
    fun `normalize - Answer a body that does not convert with body malformed`() {
        val argument = Argument.of(Map::class.java, "body", annotationMetadataOf(Body::class.java))

        val exception = normalizer.normalize(conversionErrorOf(argument, originalValue = "[]"))

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("body.malformed", exception.detailsId)
        assertEquals("description.body.malformed", exception.descriptionId)
        assertEquals(emptyMap<String, String>(), exception.values)
    }

    @Test
    fun `normalize - Answer a body that is not JSON with body malformed`() {
        val exception = normalizer.normalize(JsonSyntaxException("Unexpected end-of-input"))

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("body.malformed", exception.detailsId)
        assertEquals("description.body.malformed", exception.descriptionId)
    }

    @Test
    fun `normalize - Answer a missing parameter with parameter missing`() {
        val exception = normalizer.normalize(
            UnsatisfiedQueryValueRouteException("state", Argument.of(String::class.java, "state"))
        )

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("parameter.missing", exception.detailsId)
        assertEquals("description.parameter.missing", exception.descriptionId)
        assertEquals(mapOf("parameter" to "state"), exception.values)
    }

    @Test
    fun `normalize - Answer a missing argument with parameter missing`() {
        val exception = normalizer.normalize(UnsatisfiedArgumentException(Argument.of(String::class.java, "state")))

        assertEquals("parameter.missing", exception.detailsId)
        assertEquals(mapOf("parameter" to "state"), exception.values)
    }

    @Test
    fun `normalize - Answer a missing request attribute with internal server error`() {
        val exception = normalizer.normalize(
            UnsatisfiedRequestAttributeRouteException(
                "observedRequest",
                Argument.of(String::class.java, "observedRequest")
            )
        )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.status)
        assertEquals("internal_server_error", exception.detailsId)
    }

    @Test
    fun `normalize - Answer a missing body with body missing`() {
        val argument = Argument.of(Map::class.java, "body", annotationMetadataOf(Body::class.java))

        val exception = normalizer.normalize(UnsatisfiedBodyRouteException("body", argument))

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("body.missing", exception.detailsId)
        assertEquals("description.body.missing", exception.descriptionId)
        assertEquals(emptyMap<String, String>(), exception.values)
    }

    @Test
    fun `normalize - Answer a request no route matches with not found`() {
        val exception = normalizer.normalize(NotFoundException())

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals("not_found", exception.detailsId)
        assertEquals("description.not_found", exception.descriptionId)
    }

    @Test
    fun `normalize - Answer a method the route does not answer with method unsupported and the Allow header`() {
        val exception = normalizer.normalize(
            NotAllowedException("PUT", URI.create("/api/v1/client/invitations"), linkedSetOf("GET", "POST"))
        )

        assertInstanceOf(UnsupportedMethodException::class.java, exception)
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("method.unsupported", exception.detailsId)
        assertEquals("description.method.unsupported", exception.descriptionId)
        assertEquals(mapOf("method" to "PUT", "allowedMethods" to "GET, POST"), exception.values)
        assertEquals(mapOf(HttpHeaders.ALLOW to "GET, POST"), exception.headers)
    }

    @Test
    fun `normalize - Answer a body of a type the operation does not read with content_type unsupported`() {
        val exception = normalizer.normalize(UnsupportedMediaException("text/plain", listOf("application/json")))

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("content_type.unsupported", exception.detailsId)
        assertEquals("description.content_type.unsupported", exception.descriptionId)
        assertEquals(
            mapOf("contentType" to "text/plain", "supportedContentTypes" to "application/json"),
            exception.values
        )
    }

    @Test
    fun `normalize - Answer an Accept the operation cannot answer with accept unsupported`() {
        val exception = normalizer.normalize(NotAcceptableException(listOf("text/html"), listOf("application/json")))

        assertEquals(HttpStatus.NOT_ACCEPTABLE, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("accept.unsupported", exception.detailsId)
        assertEquals("description.accept.unsupported", exception.descriptionId)
        assertEquals(mapOf("accept" to "text/html", "producedContentTypes" to "application/json"), exception.values)
    }

    @Test
    fun `normalize - Answer a body over the limit with body too_large`() {
        listOf(ContentLengthExceededException(10, 20), BufferLengthExceededException(10, 20)).forEach {
            val exception = normalizer.normalize(it)

            assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, exception.status)
            assertTrue(exception.recoverable)
            assertEquals("body.too_large", exception.detailsId)
            assertEquals("description.body.too_large", exception.descriptionId)
        }
    }

    @Test
    fun `normalize - Answer a failed authentication with unauthorized`() {
        val exception = normalizer.normalize(AuthenticationException("Credentials do not match"))

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals("unauthorized", exception.detailsId)
    }

    @Test
    fun `normalize - Answer a failure of the routes themselves with internal server error`() {
        val exception = normalizer.normalize(DuplicateRouteException("/api/v1/client/users", emptyList()))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.status)
        assertEquals("internal_server_error", exception.detailsId)
    }

    private fun conversionErrorOf(argument: Argument<*>, originalValue: String) = ConversionErrorException(
        argument,
        object : ConversionError {
            override fun getCause() = IllegalArgumentException("Cannot convert $originalValue")
            override fun getOriginalValue() = Optional.of<Any>(originalValue)
        }
    )

    private fun annotationMetadataOf(annotation: Class<out Annotation>, value: String? = null): AnnotationMetadata =
        MutableAnnotationMetadata().apply {
            addDeclaredAnnotation(annotation.name, value?.let { mapOf<CharSequence, Any>("value" to it) }.orEmpty())
        }
}
