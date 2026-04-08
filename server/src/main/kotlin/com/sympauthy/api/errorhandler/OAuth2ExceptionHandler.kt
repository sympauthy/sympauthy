package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.mapper.OAuth2ErrorResourceMapper
import com.sympauthy.api.resource.error.OAuth2ErrorResource
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

@Singleton
class OAuth2ExceptionHandler(
    private val oAuth2ErrorResourceMapper: OAuth2ErrorResourceMapper
) : ExceptionHandler<OAuth2Exception, HttpResponse<OAuth2ErrorResource>> {

    override fun handle(request: HttpRequest<*>, exception: OAuth2Exception): HttpResponse<OAuth2ErrorResource> {
        val locale = request.locale.orDefault()
        val resource = oAuth2ErrorResourceMapper.toResource(exception, locale)
        return HttpResponseFactory.INSTANCE.status(exception.status, resource)
    }
}
