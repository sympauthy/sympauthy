package com.sympauthy.api.filter

import com.sympauthy.config.model.AdminConfig
import com.sympauthy.config.model.EnabledAdminConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
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
 * CORS filter for the admin API (`/api/v1/admin/`).
 *
 * ## Allowed origins
 * - **`admin.enabled = false`** — no CORS headers (fail-secure, filter is inactive).
 * - **`admin.enabled = true`, `admin.integrated-ui = true`** — the allowed origin is derived from
 *   `urls.root` (same root URL as the server). Only that specific origin receives CORS headers.
 * - **`admin.enabled = true`, `admin.integrated-ui = false`** — the admin UI is hosted externally
 *   and its origin is unknown, so all origins are allowed (`Access-Control-Allow-Origin: *`).
 *
 * ## Request handling
 * Same pattern as [FlowCorsFilter]:
 * - **No `Origin` header** — passed through unchanged.
 * - **OPTIONS preflight, allowed origin** — short-circuited with `200` and full CORS headers.
 * - **OPTIONS preflight, unknown origin** — short-circuited with bare `200`, no CORS headers.
 * - **Regular request, allowed origin** — proceeds through chain; CORS headers appended.
 * - **Regular request, unknown origin** — passed through unchanged.
 *
 * In wildcard mode (non-integrated UI), all origins are considered allowed.
 */
@Filter("/api/v1/admin/**")
class AdminCorsFilter(
    @Inject private val adminConfig: AdminConfig,
    @Inject private val urlsConfig: UrlsConfig
) : HttpServerFilter, Ordered {

    override fun getOrder(): Int = ServerFilterPhase.FIRST.before()

    private sealed class CorsMode {
        /** Admin disabled or config invalid — no CORS headers. */
        data object Inactive : CorsMode()
        /** Integrated UI — only the origin derived from `urls.root` is allowed. */
        data class Restricted(val allowedOrigin: String) : CorsMode()
        /** External UI — all origins allowed (`*`). */
        data object Wildcard : CorsMode()
    }

    private val corsMode: CorsMode by lazy { buildCorsMode() }

    private fun buildCorsMode(): CorsMode {
        val config = adminConfig as? EnabledAdminConfig ?: return CorsMode.Inactive
        if (!config.enabled) return CorsMode.Inactive

        if (!config.integratedUi) return CorsMode.Wildcard

        val urls = urlsConfig as? EnabledUrlsConfig ?: return CorsMode.Inactive
        val root = urls.root
        val origin = buildString {
            append(root.scheme).append("://").append(root.host)
            if (root.port != -1) append(":").append(root.port)
        }
        return CorsMode.Restricted(origin)
    }

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain
    ): Publisher<MutableHttpResponse<*>> {
        val mode = corsMode
        if (mode is CorsMode.Inactive) return chain.proceed(request)

        val origin = request.headers.origin.getOrNull()
            ?: return chain.proceed(request)

        return when (mode) {
            is CorsMode.Wildcard -> handleWildcard(request, chain)
            is CorsMode.Restricted -> handleRestricted(request, chain, origin, mode.allowedOrigin)
            is CorsMode.Inactive -> chain.proceed(request) // unreachable
        }
    }

    private fun handleWildcard(
        request: HttpRequest<*>,
        chain: ServerFilterChain
    ): Publisher<MutableHttpResponse<*>> {
        if (request.method == HttpMethod.OPTIONS) {
            val response = HttpResponse.ok<Any>()
            addWildcardCorsHeaders(response, preflight = true)
            return Flowable.just(response)
        }
        return Flowable.fromPublisher(chain.proceed(request)).map { response ->
            addWildcardCorsHeaders(response, preflight = false)
            response
        }
    }

    private fun handleRestricted(
        request: HttpRequest<*>,
        chain: ServerFilterChain,
        origin: String,
        allowedOrigin: String
    ): Publisher<MutableHttpResponse<*>> {
        val isAllowed = origin == allowedOrigin

        if (request.method == HttpMethod.OPTIONS) {
            val response = HttpResponse.ok<Any>()
            if (isAllowed) addRestrictedCorsHeaders(response, origin, preflight = true)
            return Flowable.just(response)
        }

        if (!isAllowed) return chain.proceed(request)

        return Flowable.fromPublisher(chain.proceed(request)).map { response ->
            addRestrictedCorsHeaders(response, origin, preflight = false)
            response
        }
    }

    private fun addWildcardCorsHeaders(response: MutableHttpResponse<*>, preflight: Boolean) {
        response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        if (preflight) {
            response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS")
            response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization")
            response.headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "86400")
        }
    }

    private fun addRestrictedCorsHeaders(
        response: MutableHttpResponse<*>,
        origin: String,
        preflight: Boolean
    ) {
        response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
        response.headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN)
        if (preflight) {
            response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS")
            response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization")
            response.headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "86400")
        }
    }
}
