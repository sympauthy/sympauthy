package com.sympauthy.api.errorhandler

import com.sympauthy.monitoring.Conditions
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.cookie.CookieSizeExceededException
import io.micronaut.http.exceptions.BufferLengthExceededException
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.server.exceptions.BufferLengthExceededHandler
import io.micronaut.http.server.exceptions.ContentLengthExceededHandler
import io.micronaut.http.server.exceptions.ConversionErrorHandler
import io.micronaut.http.server.exceptions.CookieSizeExceededHandler
import io.micronaut.http.server.exceptions.DuplicateRouteHandler
import io.micronaut.http.server.exceptions.HttpStatusHandler
import io.micronaut.http.server.exceptions.JacksonExceptionHandler
import io.micronaut.http.server.exceptions.JsonExceptionHandler
import io.micronaut.http.server.exceptions.NotAllowedException
import io.micronaut.http.server.exceptions.NotAllowedExceptionHandler
import io.micronaut.http.server.exceptions.URISyntaxHandler
import io.micronaut.http.server.exceptions.UnsatisfiedArgumentHandler
import io.micronaut.http.server.exceptions.UnsatisfiedRouteHandler
import io.micronaut.json.JsonSyntaxException
import io.micronaut.security.authentication.AuthenticationException
import io.micronaut.security.authentication.AuthenticationExceptionHandler
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler
import io.micronaut.security.errors.OauthErrorResponseException
import io.micronaut.security.errors.OauthErrorResponseExceptionHandler
import io.micronaut.security.oauth2.endpoint.authorization.response.AuthorizationErrorResponseException
import io.micronaut.security.oauth2.endpoint.authorization.response.AuthorizationErrorResponseExceptionHandler
import io.micronaut.validation.exceptions.ConstraintExceptionHandler
import io.micronaut.web.router.exceptions.DuplicateRouteException
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException
import tools.jackson.core.JacksonException
import java.net.URISyntaxException

/**
 * Routes every failure answering a request through the [ExceptionNormalizer] and the
 * [ThrowableExceptionHandler], so each one reaches the caller in the body its surface answers with: the
 * one RFC 6749 defines on the protocol endpoints that answer with it, and the ordinary one, with an
 * `error_code`, everywhere else.
 *
 * The handler for [Throwable] is not enough on its own: the framework answers a failure with the
 * handler whose type is closest to it, so a handler a framework registers for a narrower type wins
 * over it and answers in a body of its own. Every such handler is replaced here by one for the same
 * type.
 *
 * A replaced type the [ExceptionNormalizer] gives no answer of its own is a `500`, as every failure
 * it does not recognise is. Such a type is a failure of the server — a cookie too large to set, two
 * routes matching one request, a URI or a JSON document this server built — or one no route here
 * raises: the refusals of the framework's own OAuth2 endpoints, and a constraint violation where no
 * argument declares a constraint.
 */
@Factory
class ExceptionHandlerFactory(
    @Inject private val exceptionNormalizer: ExceptionNormalizer,
    @Inject private val exceptionHandler: LocalizedExceptionHandler,
    @Inject private val conditions: Conditions
) {

    @Singleton
    @Replaces(DefaultAuthorizationExceptionHandler::class)
    fun authorizationExceptionHandler() = exceptionHandler<AuthorizationException>()

    @Singleton
    @Replaces(AuthenticationExceptionHandler::class)
    fun authenticationExceptionHandler() = exceptionHandler<AuthenticationException>()

    @Singleton
    @Replaces(HttpStatusHandler::class)
    fun httpStatusExceptionHandler() = exceptionHandler<HttpStatusException>()

    @Singleton
    @Replaces(NotAllowedExceptionHandler::class)
    fun notAllowedExceptionHandler() = exceptionHandler<NotAllowedException>()

    @Singleton
    @Replaces(ConversionErrorHandler::class)
    fun conversionErrorExceptionHandler() = exceptionHandler<ConversionErrorException>()

    @Singleton
    @Replaces(UnsatisfiedRouteHandler::class)
    fun unsatisfiedRouteExceptionHandler() = exceptionHandler<UnsatisfiedRouteException>()

    @Singleton
    @Replaces(UnsatisfiedArgumentHandler::class)
    fun unsatisfiedArgumentExceptionHandler() = exceptionHandler<UnsatisfiedArgumentException>()

    @Singleton
    @Replaces(JsonExceptionHandler::class)
    fun jsonSyntaxExceptionHandler() = exceptionHandler<JsonSyntaxException>()

    @Singleton
    @Replaces(ContentLengthExceededHandler::class)
    fun contentLengthExceededExceptionHandler() = exceptionHandler<ContentLengthExceededException>()

    @Singleton
    @Replaces(BufferLengthExceededHandler::class)
    fun bufferLengthExceededExceptionHandler() = exceptionHandler<BufferLengthExceededException>()

    @Singleton
    @Replaces(CookieSizeExceededHandler::class)
    fun cookieSizeExceededExceptionHandler() = exceptionHandler<CookieSizeExceededException>()

    @Singleton
    @Replaces(DuplicateRouteHandler::class)
    fun duplicateRouteExceptionHandler() = exceptionHandler<DuplicateRouteException>()

    @Singleton
    @Replaces(URISyntaxHandler::class)
    fun uriSyntaxExceptionHandler() = exceptionHandler<URISyntaxException>()

    @Singleton
    @Replaces(JacksonExceptionHandler::class)
    fun jacksonExceptionHandler() = exceptionHandler<JacksonException>()

    @Singleton
    @Replaces(ConstraintExceptionHandler::class)
    fun constraintViolationExceptionHandler() = exceptionHandler<ConstraintViolationException>()

    @Singleton
    @Replaces(OauthErrorResponseExceptionHandler::class)
    fun oauthErrorResponseExceptionHandler() = exceptionHandler<OauthErrorResponseException>()

    @Singleton
    @Replaces(AuthorizationErrorResponseExceptionHandler::class)
    fun authorizationErrorResponseExceptionHandler() = exceptionHandler<AuthorizationErrorResponseException>()

    @Singleton
    fun throwableExceptionHandler() = exceptionHandler<Throwable>()

    private inline fun <reified T : Throwable> exceptionHandler() =
        ThrowableExceptionHandler<T>(exceptionNormalizer, exceptionHandler, conditions)
}
