package com.sympauthy.api.errorhandler

import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.loggerForClass
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler

class ThrowableExceptionHandler<T : Throwable>(
    private val exceptionNormalizer: ExceptionNormalizer,
    private val exceptionHandler: LocalizedExceptionHandler,
    private val conditionMonitor: ConditionMonitor
) : ExceptionHandler<T, HttpResponse<*>> {

    private val logger = loggerForClass()

    /**
     * Answer the [throwable] with the response its normalized form renders to, logging it first at WARN
     * where that form is a `5xx`.
     *
     * Every `5xx` is logged and not only the uncoded one: a failure telling the caller the server broke
     * says nothing else on the wire, so the row, the column and the cause reach nobody unless they reach
     * the log. A `4xx` is the caller's to read and is not logged.
     *
     * A `5xx` caused by a dependency being out of reach is the exception, reported to its condition
     * instead: the condition writes the ERROR once, and a WARN per request on top of it would be the line
     * per failure the condition exists to remove.
     */
    override fun handle(request: HttpRequest<*>, throwable: T): HttpResponse<*> {
        val httpException = exceptionNormalizer.normalize(throwable)
        if (httpException.status.code >= 500 && !conditionMonitor.absorb(throwable)) {
            logger.warn(throwable) { "Server failure ${httpException.detailsId} ${httpException.values}" }
        }
        return exceptionHandler.handle(request, httpException)
    }
}
