package com.sympauthy.api.errorhandler

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.Conditions
import com.sympauthy.monitoring.loggerForClass
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.r2dbc.spi.R2dbcNonTransientResourceException
import io.r2dbc.spi.R2dbcTimeoutException
import io.r2dbc.spi.R2dbcTransientResourceException

class ThrowableExceptionHandler<T : Throwable>(
    private val exceptionNormalizer: ExceptionNormalizer,
    private val exceptionHandler: LocalizedExceptionHandler,
    private val conditions: Conditions
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
     * A `5xx` caused by the database being out of reach is the exception. Its condition has already
     * written the ERROR saying so, and a WARN per request on top of it would be the line per failure the
     * condition exists to remove.
     */
    override fun handle(request: HttpRequest<*>, throwable: T): HttpResponse<*> {
        val httpException = exceptionNormalizer.normalize(throwable)
        if (httpException.status.code >= 500 && !isReportedByTheDatabase(throwable)) {
            logger.warn(throwable) { "Server failure ${httpException.detailsId} ${httpException.values}" }
        }
        return exceptionHandler.handle(request, httpException)
    }

    /**
     * Whether [throwable] is the database being out of reach rather than a query that failed, while its
     * condition says it is degraded.
     */
    private fun isReportedByTheDatabase(throwable: Throwable): Boolean {
        val unreachable = generateSequence(throwable) { it.cause }.any {
            it is R2dbcNonTransientResourceException || it is R2dbcTransientResourceException ||
                it is R2dbcTimeoutException
        }
        return unreachable && conditions.isDegraded(Condition.Database)
    }
}
