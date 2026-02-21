package com.sympauthy.api.errorhandler

import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.util.loggerForClass
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler

class ThrowableExceptionHandler<T : Throwable>(
    private val exceptionNormalizer: ExceptionNormalizer,
    private val exceptionHandler: LocalizedExceptionHandler
) : ExceptionHandler<T, HttpResponse<ErrorResource>> {

    private val logger = loggerForClass()

    override fun handle(request: HttpRequest<*>, throwable: T): HttpResponse<ErrorResource> {
        val httpException = exceptionNormalizer.normalize(throwable)
        if (httpException.detailsId == "internal_server_error") {
            logger.error("Unexpected error occurred: ${throwable.message}", throwable)
        }
        return exceptionHandler.handle(request, httpException)
    }
}
