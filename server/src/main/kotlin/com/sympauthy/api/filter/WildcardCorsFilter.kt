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
 * Wildcard CORS filter for endpoints that allow all origins (`Access-Control-Allow-Origin: *`).
 *
 * Covers:
 * - **OpenID discovery** (`/.well-known`) — public metadata per OIDC spec.
 * - **OAuth 2.0** (`/api/oauth2`) — token and revocation endpoints called directly by public clients (e.g. SPAs).
 * - **OpenID Connect** (`/api/openid`) — the UserInfo endpoint, which an SPA calls cross-origin with the
 *   access token it just obtained.
 *
 * All of them authenticate from an explicit `Authorization` (or `DPoP`) header rather than from cookies,
 * and no `Access-Control-Allow-Credentials` is ever sent, so allowing every origin does not let a
 * third-party page act on behalf of a signed-in user: it would need a token it does not have.
 *
 * The client API (`/api/v1/client`) is deliberately **not** covered. Its endpoints are secured by client
 * scopes, which `ScopeManager.parseRequestedClientScopes` only grants in the `client_credentials` flow,
 * so reaching them requires a client secret and they are server-to-server by design. A browser cannot
 * legitimately authenticate there, and advertising CORS would suggest otherwise.
 *
 * ## Request handling
 * - **No `Origin` header** — not a browser CORS request; passed through unchanged.
 * - **OPTIONS preflight** — short-circuited with `200` and wildcard CORS headers.
 * - **Regular request with `Origin`** — proceeds through the chain; wildcard CORS header appended.
 *
 * The set of allowed request headers is uniform across all paths and defined by [CorsPreflightHeaders].
 */
@Filter(
    "/.well-known/**",
    "/api/oauth2/**",
    "/api/openid/**"
)
class WildcardCorsFilter(
    @Inject private val corsPreflightHeaders: CorsPreflightHeaders
) : HttpServerFilter, Ordered {

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
            corsPreflightHeaders.addTo(response, ALLOWED_METHODS)
        }
    }

    companion object {
        private const val ALLOWED_METHODS = "GET, POST, OPTIONS"
    }
}
