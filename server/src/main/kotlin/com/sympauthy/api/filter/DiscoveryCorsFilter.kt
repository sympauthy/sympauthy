package com.sympauthy.api.filter

import io.micronaut.core.order.Ordered
import io.micronaut.http.*
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.filter.ServerFilterPhase
import io.reactivex.rxjava3.core.Flowable
import org.reactivestreams.Publisher
import kotlin.jvm.optionals.getOrNull

/**
 * CORS filter for OpenID discovery endpoints (`/.well-known/`).
 *
 * These endpoints serve public metadata (OpenID configuration, JWKS) and, per standard OIDC practice,
 * allow all origins (`Access-Control-Allow-Origin: *`).
 *
 * ## Request handling
 * - **No `Origin` header** — not a browser CORS request; passed through unchanged.
 * - **OPTIONS preflight** — short-circuited with `200` and wildcard CORS headers.
 * - **Regular request with `Origin`** — proceeds through the chain; wildcard CORS header appended.
 */
@Filter("/.well-known/**")
class DiscoveryCorsFilter : HttpServerFilter, Ordered {

    override fun getOrder(): Int = ServerFilterPhase.FIRST.before()

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain
    ): Publisher<MutableHttpResponse<*>> {
        val origin = request.headers.origin.getOrNull()
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

    private fun addCorsHeaders(response: MutableHttpResponse<*>, preflight: Boolean) {
        response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        if (preflight) {
            response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS")
            response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization")
            response.headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "86400")
        }
    }
}
