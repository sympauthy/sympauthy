package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.ErrorResourceMapper
import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.exception.LocalizedException
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

@Singleton
class LocalizedExceptionHandler(
    private val errorResourceMapper: ErrorResourceMapper
) : ExceptionHandler<LocalizedException, HttpResponse<ErrorResource>> {

    /**
     * Answer the [exception] with the body it renders to in the caller's locale, and with the headers
     * it carries.
     *
     * A header belongs to the failure rather than to this handler — see [LocalizedHttpException.headers]
     * — so every refusal that has one is answered alike, and one raised below the `api` layer simply has
     * none.
     */
    override fun handle(request: HttpRequest<*>, exception: LocalizedException): HttpResponse<ErrorResource> {
        val locale = request.locale.orDefault()
        val resource = errorResourceMapper.toResource(exception, locale)
        val response = HttpResponseFactory.INSTANCE.status(
            HttpStatus.valueOf(resource.status),
            resource
        )
        (exception as? LocalizedHttpException)?.headers?.forEach { (name, value) -> response.header(name, value) }
        return response
    }
}
