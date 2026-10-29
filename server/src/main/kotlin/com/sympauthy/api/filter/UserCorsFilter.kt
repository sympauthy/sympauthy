package com.sympauthy.api.filter

import io.micronaut.core.order.Ordered
import io.micronaut.http.*
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.filter.ServerFilterPhase
import io.reactivex.rxjava3.core.Flowable
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import kotlin.jvm.optionals.getOrNull

/**
 * CORS filter for the user API (`/api/v1/user`), which allows all origins (`Access-Control-Allow-Origin: *`).
 *
 * Its caller is a page of any client of the deployment, signed in as the person and holding their access token,
 * so no origin is known to this server ahead of the request. Like the endpoints [WildcardCorsFilter] covers, it
 * authenticates from an explicit `Authorization` (or `DPoP`) header rather than from cookies, and no
 * `Access-Control-Allow-Credentials` is ever sent, so allowing every origin does not let a third-party page act
 * on behalf of a signed-in person: it would need a token it does not have.
 *
 * It is a filter of its own rather than one more path of [WildcardCorsFilter] so that the methods a preflight
 * advertises are the ones this surface answers.
 *
 * ## Request handling
 * - **No `Origin` header** — not a browser CORS request; passed through unchanged.
 * - **OPTIONS preflight** — short-circuited with `200` and wildcard CORS headers.
 * - **Regular request with `Origin`** — proceeds through the chain; wildcard CORS header appended.
 */
@Filter("/api/v1/user/**")
class UserCorsFilter(
    @Inject private val corsPreflightHeaders: CorsPreflightHeaders
) : HttpServerFilter, Ordered {

    override fun getOrder(): Int = ServerFilterPhase.FIRST.before()

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain
    ): Publisher<MutableHttpResponse<*>> {
        request.headers.origin.getOrNull()
            ?: return chain.proceed(request)

        if (request.method == HttpMethod.OPTIONS) {
            val response = HttpResponse.ok<Any>()
            addCorsHeaders(response, preflight = true)
            return Flowable.just(response)
        }

        return Flowable.fromPublisher(chain.proceed(request)).map { response ->
            addCorsHeaders(response, preflight = false)
            response
        }
    }

    /**
     * A response that is not a preflight exposes `WWW-Authenticate`, so a page reads the challenge a refusal
     * carries.
     */
    private fun addCorsHeaders(response: MutableHttpResponse<*>, preflight: Boolean) {
        response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        if (preflight) {
            corsPreflightHeaders.addTo(response, ALLOWED_METHODS)
        } else {
            response.headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.WWW_AUTHENTICATE)
        }
    }

    companion object {
        private const val ALLOWED_METHODS = "GET, PATCH, OPTIONS"
    }
}
