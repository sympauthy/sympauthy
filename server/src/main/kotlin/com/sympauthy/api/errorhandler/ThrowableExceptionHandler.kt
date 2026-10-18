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

    /**
     * Answer the [throwable] with the response its normalized form renders to, logging it first where
     * that form is a `5xx`.
     *
     * Every `5xx` is logged and not only the uncoded one: a failure telling the caller the server broke
     * says nothing else on the wire, so the row, the column and the cause reach nobody unless they reach
     * the log. A `4xx` is the caller's to read and is not logged.
     */
    override fun handle(request: HttpRequest<*>, throwable: T): HttpResponse<ErrorResource> {
        val httpException = exceptionNormalizer.normalize(throwable)
        if (httpException.status.code >= 500) {
            logger.error("Server failure ${httpException.detailsId} ${httpException.values}", throwable)
        }
        return exceptionHandler.handle(request, httpException)
    }
}
