package com.sympauthy.api.errorhandler

import com.sympauthy.api.controller.oauth2.IntrospectionController.Companion.OAUTH2_INTROSPECTION_ENDPOINT
import com.sympauthy.api.controller.oauth2.RevokeController.Companion.OAUTH2_REVOKE_ENDPOINT
import com.sympauthy.api.controller.oauth2.TokenController.Companion.OAUTH2_TOKEN_ENDPOINT
import com.sympauthy.api.exception.AccessTokenChallengeException
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.toOAuth2Exception
import com.sympauthy.api.mapper.ErrorResourceMapper
import com.sympauthy.api.mapper.OAuth2ErrorResourceMapper
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_CLIENT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.SERVER_ERROR
import com.sympauthy.config.model.isInvalidConfig
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.mapper.LocalizedErrorMapper
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.loggerForClass
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
    private val localizedErrorMapper: LocalizedErrorMapper,
    private val errorResourceMapper: ErrorResourceMapper,
    private val oAuth2ErrorResourceMapper: OAuth2ErrorResourceMapper,
    private val conditionMonitor: ConditionMonitor
) : ExceptionHandler<LocalizedException, HttpResponse<*>> {

    private val logger = loggerForClass()

    override fun handle(request: HttpRequest<*>, exception: LocalizedException): HttpResponse<*> =
        handle(request, exception, exception)

    /**
     * Answer the [exception] with the body it renders to in the caller's locale, and with the headers
     * it carries, logging the [failure] it answers first where it is answered with a `5xx`.
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
     *
     * The [failure] is what the server failed with, and the [exception] the form it is answered in: the two
     * are one, unless the [exception] was normalized from the [failure].
     *
     * Every `5xx` is logged and not only the uncoded one: a failure telling the caller the server broke
     * says nothing else on the wire, so the row, the column and the cause reach nobody unless they reach
     * the log. A `4xx` is the caller's to read and is not logged.
     *
     * A `5xx` caused by a dependency being out of reach is the exception, reported to its condition
     * instead: the condition writes the ERROR once, and a WARN per request on top of it would be the line
     * per failure the condition exists to remove. So is a configuration found invalid, which the verdict on
     * it already wrote once at startup.
     *
     * A failure no code names — one that reached the edge as anything but a [LocalizedException] — is
     * written at ERROR rather than WARN, asking for an issue, the departure from the logging standard
     * `docs/design/logging.md` argues.
     */
    fun handle(request: HttpRequest<*>, exception: LocalizedException, failure: Throwable): HttpResponse<*> {
        val answered = if (exception is AccessTokenChallengeException && !acceptsAccessToken(request)) {
            exception.withoutChallenge()
        } else {
            exception
        }
        val locale = request.locale.orDefault()
        val status = localizedErrorMapper.toHttpStatus(answered)
        if (status.code >= 500) {
            record(failure, answered)
        }
        val body = if (answersWithOAuth2Error(request)) {
            oAuth2ErrorResourceMapper.toResource(answered.toOAuth2Exception(oAuth2ErrorCodeOf(status)), locale)
        } else {
            errorResourceMapper.toResource(answered, locale)
        }
        val response = HttpResponseFactory.INSTANCE.status(status, body)
        (answered as? LocalizedHttpException)?.headers?.forEach { (name, value) -> response.header(name, value) }
        return response
    }

    /**
     * Write the [failure] answered as [answered] to the log, unless a line already said it: a condition
     * [ConditionMonitor.absorb] reports it to, or the configuration's verdict for a configuration found invalid.
     */
    private fun record(failure: Throwable, answered: LocalizedException) {
        if (failure is LocalizedException && failure.isInvalidConfig) return
        if (conditionMonitor.absorb(failure)) return
        if (failure is LocalizedException) {
            logger.warn(failure) { "Server failure ${answered.detailsId} ${answered.values}" }
        } else {
            logger.error(failure) {
                "Unhandled failure ${answered.detailsId}: this is most likely a bug in SympAuthy. " +
                    "Please open an issue at $ISSUES_URL."
            }
        }
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
     * a method, a media type or a body reached none. The router answers a path with a trailing slash as the
     * path without one, and so does this.
     */
    private fun answersWithOAuth2Error(request: HttpRequest<*>): Boolean =
        request.path.removeSuffix("/") in OAUTH2_ERROR_ENDPOINTS

    /**
     * The protocol code a failure answered with [status] on one of those endpoints names.
     *
     * An endpoint names its own refusals as an `OAuth2Exception`, which another handler answers, so a failure
     * reaching this one there names no protocol code: a `401` is a client that did not authenticate, which is
     * `invalid_client`, any other `4xx` is a request refused before the endpoint read it, which is
     * `invalid_request`, and a `5xx` is `server_error`.
     */
    private fun oAuth2ErrorCodeOf(status: HttpStatus): OAuth2ErrorCode = when {
        status.code >= 500 -> SERVER_ERROR
        status == HttpStatus.UNAUTHORIZED -> INVALID_CLIENT
        else -> INVALID_REQUEST
    }

    private companion object {
        const val ISSUES_URL = "https://github.com/sympauthy/sympauthy/issues"

        val OAUTH2_ERROR_ENDPOINTS = setOf(
            OAUTH2_TOKEN_ENDPOINT,
            OAUTH2_INTROSPECTION_ENDPOINT,
            OAUTH2_REVOKE_ENDPOINT
        )
    }
}
