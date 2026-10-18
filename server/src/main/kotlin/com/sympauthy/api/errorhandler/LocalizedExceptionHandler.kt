package com.sympauthy.api.errorhandler

import com.sympauthy.api.exception.AccessTokenChallengeException
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.ErrorResourceMapper
import com.sympauthy.api.resource.error.ErrorResource
import com.sympauthy.exception.LocalizedException
import com.sympauthy.security.SecurityRule
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.security.annotation.Secured
import io.micronaut.web.router.MethodBasedRouteMatch
import io.micronaut.web.router.RouteAttributes
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
     * none. The one header the request decides is the challenge of an [AccessTokenChallengeException], which
     * is answered without it where [request] reached no route accepting an access token: a challenge would
     * send the caller after a token that route does not take.
     */
    override fun handle(request: HttpRequest<*>, exception: LocalizedException): HttpResponse<ErrorResource> {
        val answered = if (exception is AccessTokenChallengeException && !acceptsAccessToken(request)) {
            exception.withoutChallenge()
        } else {
            exception
        }
        val locale = request.locale.orDefault()
        val resource = errorResourceMapper.toResource(answered, locale)
        val response = HttpResponseFactory.INSTANCE.status(
            HttpStatus.valueOf(resource.status),
            resource
        )
        (answered as? LocalizedHttpException)?.headers?.forEach { (name, value) -> response.header(name, value) }
        return response
    }

    /**
     * Whether [request] reached a route an access token is accepted on: one whose `@Secured` names a role or a
     * scope a token grants. A request no route answers did not, and neither did one to a route gated on the
     * state or open to anyone.
     */
    private fun acceptsAccessToken(request: HttpRequest<*>): Boolean {
        val route = RouteAttributes.getRouteMatch(request).orElse(null) as? MethodBasedRouteMatch<*, *>
            ?: return false
        val roles = route.getValue(Secured::class.java, Array<String>::class.java).orElse(null)
            ?: return false
        return roles.any(SecurityRule::isGrantedByAccessToken)
    }
}
