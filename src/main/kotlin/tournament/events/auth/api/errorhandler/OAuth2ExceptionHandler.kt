package tournament.events.auth.api.errorhandler

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Inject
import jakarta.inject.Singleton
import tournament.events.auth.api.exception.OAuth2Exception
import tournament.events.auth.api.mapper.OAuth2ErrorResourceMapper
import tournament.events.auth.api.resource.error.OAuth2ErrorResource
import tournament.events.auth.util.orDefault

@Singleton
class OAuth2ExceptionHandler(
    @Inject private val resourceMapper: OAuth2ErrorResourceMapper
) : ExceptionHandler<OAuth2Exception, HttpResponse<OAuth2ErrorResource>> {

    override fun handle(request: HttpRequest<*>, exception: OAuth2Exception): HttpResponse<OAuth2ErrorResource> {
        val locale = request.locale.orDefault()
        return HttpResponseFactory.INSTANCE.status(
            exception.status, resourceMapper.toResource(exception, locale)
        )
    }
}
