package com.sympauthy.api.filter

import com.sympauthy.api.controller.OAUTH2_SURFACE
import com.sympauthy.api.controller.OPENID_SURFACE
import com.sympauthy.api.controller.USER_SURFACE
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
 * The CORS filter for the surfaces answering every origin (`Access-Control-Allow-Origin: *`): OpenID discovery
 * (`/.well-known`), the OAuth 2.0 and OpenID Connect endpoints, the user API, and the brand document
 * (`/branding/brand.json`; the rest of `/branding` needs no CORS). Why each answers every origin, and why the
 * client API has no filter, is `docs/standards/api-standard.md` and `docs/standards/api-layer-code-standard.md`.
 *
 * ## Request handling
 * - **No `Origin` header** — not a browser CORS request; passed through unchanged.
 * - **OPTIONS preflight** — short-circuited with `200` and wildcard CORS headers.
 * - **Regular request with `Origin`** — proceeds through the chain; wildcard CORS header appended.
 *
 * The set of allowed request headers is uniform across all paths and defined by [CorsPreflightHeaders]. The
 * methods a preflight advertises are the ones the surface it reached answers.
 */
@Filter(
    "/.well-known/**",
    "$OAUTH2_SURFACE/**",
    "$OPENID_SURFACE/**",
    "$USER_SURFACE/**",
    BRAND_DOCUMENT
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
            addCorsHeaders(response, allowedMethods = allowedMethodsOf(request))
            return Flowable.just(response)
        }

        return Flowable.fromPublisher(chain.proceed(request)).map { response ->
            addCorsHeaders(response, allowedMethods = null)
            response
        }
    }

    private fun allowedMethodsOf(request: HttpRequest<*>): String = when {
        request.path.startsWith("$USER_SURFACE/") -> USER_ALLOWED_METHODS
        request.path == BRAND_DOCUMENT -> BRAND_DOCUMENT_ALLOWED_METHODS
        else -> ALLOWED_METHODS
    }

    /**
     * A preflight advertises [allowedMethods]. A response that is not one, where they are null, exposes
     * `WWW-Authenticate`, so a page reads the challenge a refusal carries.
     */
    private fun addCorsHeaders(response: MutableHttpResponse<*>, allowedMethods: String?) {
        response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        if (allowedMethods != null) {
            corsPreflightHeaders.addTo(response, allowedMethods)
        } else {
            response.headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.WWW_AUTHENTICATE)
        }
    }

    companion object {
        private const val ALLOWED_METHODS = "GET, POST, OPTIONS"
        private const val USER_ALLOWED_METHODS = "GET, PATCH, OPTIONS"
        private const val BRAND_DOCUMENT_ALLOWED_METHODS = "GET, OPTIONS"
    }
}

private const val BRAND_DOCUMENT = "/branding/brand.json"
