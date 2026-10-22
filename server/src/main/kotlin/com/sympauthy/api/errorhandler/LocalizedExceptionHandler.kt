package com.sympauthy.api.errorhandler

import com.sympauthy.api.controller.oauth2.IntrospectionController.Companion.OAUTH2_INTROSPECTION_ENDPOINT
import com.sympauthy.api.controller.oauth2.RevokeController.Companion.OAUTH2_REVOKE_ENDPOINT
import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.exception.AccessTokenChallengeException
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.toOAuth2Exception
import com.sympauthy.api.mapper.ErrorResourceMapper
import com.sympauthy.api.mapper.OAuth2ErrorResourceMapper
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.SERVER_ERROR
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
    private val errorResourceMapper: ErrorResourceMapper,
    private val oAuth2ErrorResourceMapper: OAuth2ErrorResourceMapper
) : ExceptionHandler<LocalizedException, HttpResponse<*>> {

    /**
     * Answer the [exception] with the body it renders to in the caller's locale, and with the headers
     * it carries.
     *
     * The body is the one RFC 6749 §5.2 defines where [request] is addressed to an endpoint whose errors
     * that body is — see [answersWithOAuth2Error] — and the ordinary error body everywhere else. The
     * status is the one the [exception] renders to either way.
     *
     * A header belongs to the failure rather than to this handler — see [LocalizedHttpException.headers]
     * — so every refusal that has one is answered alike, and one raised below the `api` layer simply has
     * none. The one header the request decides is the challenge of an [AccessTokenChallengeException], which
     * is answered without it where [request] reached no route accepting an access token: a challenge would
     * send the caller after a token that route does not take.
     */
    override fun handle(request: HttpRequest<*>, exception: LocalizedException): HttpResponse<*> {
        val answered = if (exception is AccessTokenChallengeException && !acceptsAccessToken(request)) {
            exception.withoutChallenge()
        } else {
            exception
        }
        val locale = request.locale.orDefault()
        val resource = errorResourceMapper.toResource(answered, locale)
        val status = HttpStatus.valueOf(resource.status)
        val body = if (answersWithOAuth2Error(request)) {
            val errorCode = if (status.code >= 500) SERVER_ERROR else INVALID_REQUEST
            oAuth2ErrorResourceMapper.toResource(answered.toOAuth2Exception(errorCode), locale)
        } else {
            resource
        }
        val response = HttpResponseFactory.INSTANCE.status(status, body)
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

    /**
     * Whether [request] is addressed to the token, the introspection or the revocation endpoint, whose
     * errors RFC 6749 §5.2, RFC 7662 §2.3 and RFC 7009 §2.2.1 answer in the body RFC 6749 defines.
     *
     * It reads the path rather than the route the request reached, because a request the framework refuses
     * a method, a media type or a body reached none. An endpoint names its own refusals as an `OAuth2Exception`,
     * which another handler answers, so a failure reaching this one there names no protocol code: a `4xx` is a
     * request refused before the endpoint read it, which is `invalid_request`, and a `5xx` is `server_error`.
     */
    private fun answersWithOAuth2Error(request: HttpRequest<*>): Boolean = request.path in OAUTH2_ERROR_ENDPOINTS

    private companion object {
        val OAUTH2_ERROR_ENDPOINTS = setOf(
            OAUTH2_TOKEN_ENDPOINT,
            OAUTH2_INTROSPECTION_ENDPOINT,
            OAUTH2_REVOKE_ENDPOINT
        )
    }
}
