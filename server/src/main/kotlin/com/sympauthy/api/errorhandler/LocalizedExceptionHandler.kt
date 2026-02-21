package com.sympauthy.api.errorhandler

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

    override fun handle(request: HttpRequest<*>, exception: LocalizedException): HttpResponse<ErrorResource> {
        val locale = request.locale.orDefault()
        val resource = errorResourceMapper.toResource(exception, locale)
        return HttpResponseFactory.INSTANCE.status(
            HttpStatus.valueOf(resource.status),
            resource
        )
    }
}
