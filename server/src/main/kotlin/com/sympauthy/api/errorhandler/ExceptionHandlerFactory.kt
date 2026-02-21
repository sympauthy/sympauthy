package com.sympauthy.api.errorhandler

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * This class replaces all defaults exception handler provided by frameworks (like Micronaut security)
 * by our own [ThrowableExceptionHandler].
 *
 * This helps to make our error messages uniform across the whole application
 * and to keep the logic factorized into the [ExceptionNormalizer] and the [ThrowableExceptionHandler].
 */
@Factory
class ExceptionHandlerFactory(
    @Inject private val exceptionNormalizer: ExceptionNormalizer,
    @Inject private val exceptionHandler: LocalizedExceptionHandler
) {

    @Singleton
    @Replaces(DefaultAuthorizationExceptionHandler::class)
    fun authorizationExceptionHandler() = exceptionHandler<AuthorizationException>()

    @Singleton
    fun throwableExceptionHandler() = exceptionHandler<Throwable>()

    private inline fun <reified T : Throwable> exceptionHandler() =
        ThrowableExceptionHandler<T>(exceptionNormalizer, exceptionHandler)
}
