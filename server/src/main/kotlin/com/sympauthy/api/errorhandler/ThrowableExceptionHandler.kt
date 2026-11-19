package com.sympauthy.api.errorhandler

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler

class ThrowableExceptionHandler<T : Throwable>(
    private val exceptionNormalizer: ExceptionNormalizer,
    private val exceptionHandler: LocalizedExceptionHandler
) : ExceptionHandler<T, HttpResponse<*>> {

    /**
     * Answer the [throwable] with the response its normalized form renders to, handing the [throwable] itself
     * over as the failure to record: the normalized form's stack is the normalizer's, and the [throwable]'s is
     * where the server failed.
     */
    override fun handle(request: HttpRequest<*>, throwable: T): HttpResponse<*> {
        return exceptionHandler.handle(request, exceptionNormalizer.normalize(throwable), throwable)
    }
}
