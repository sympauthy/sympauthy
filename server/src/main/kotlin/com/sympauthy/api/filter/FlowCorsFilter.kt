package com.sympauthy.api.filter

import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.EnabledAuthorizationFlowsConfig
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
 * CORS filter scoped to the flow API (`/api/v1/flow/`).
 *
 * ## Problem
 * Browsers enforce the Same-Origin Policy: a web page at `http://localhost:5173` cannot make API
 * calls to `http://localhost:8080` unless the server explicitly permits it via CORS headers.
 * The global Micronaut CORS filter is disabled (`micronaut.server.cors.enabled: false`) to avoid
 * allowing all origins by default. This filter replaces it with a restricted, flow-aware policy.
 *
 * ## Allowed origins
 * At first use the filter collects the origin (`scheme://host:port`) from every URI declared across
 * all [WebAuthorizationFlow] instances — both the default bundled flow and user-configured flows —
 * covering `signInUri`, `collectClaimsUri`, `validateClaimsUri`, and `errorUri`. The result is
 * cached as an immutable set for the lifetime of the application.
 * If no flows are configured the set is empty and no CORS headers are ever added (fail-secure).
 *
 * ## Request handling
 * - **No `Origin` header** — not a browser CORS request; passed through unchanged.
 * - **OPTIONS preflight, allowed origin** — short-circuited with `200` and the full set of
 *   preflight headers (`Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`,
 *   `Access-Control-Max-Age`). The request never reaches the security filter, which would otherwise
 *   reject it for missing a JWT token.
 * - **OPTIONS preflight, unknown origin** — short-circuited with a bare `200` and no CORS headers;
 *   the browser receives no permission and blocks the subsequent request.
 * - **Regular request, allowed origin** — proceeds through the chain normally, then
 *   `Access-Control-Allow-Origin` and `Vary: Origin` are appended to the response.
 * - **Regular request, unknown origin** — passed through unchanged; no CORS headers are added.
 *
 * ## Filter order
 * Runs at [ServerFilterPhase.SECURITY].order() - 10, i.e. before Micronaut Security, so that
 * OPTIONS preflights are handled before the security filter can reject unauthenticated requests.
 */
@Filter("/api/v1/flow/**")
class FlowCorsFilter(
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val authorizationFlowManager: AuthorizationFlowManager
) : HttpServerFilter, Ordered {

    override fun getOrder(): Int = ServerFilterPhase.SECURITY.before()

    private val allowedOrigins: Set<String> by lazy { buildAllowedOrigins() }

    private fun buildAllowedOrigins(): Set<String> {
        val origins = mutableSetOf<String>()

        // Default bundled flow (same server, but include for completeness)
        try {
            origins += extractOrigins(authorizationFlowManager.defaultWebAuthorizationFlow)
        } catch (_: Exception) {
        }

        // User-configured flows
        (authorizationFlowsConfig as? EnabledAuthorizationFlowsConfig)
            ?.flows
            ?.filterIsInstance<WebAuthorizationFlow>()
            ?.forEach { origins += extractOrigins(it) }

        return origins
    }

    private fun extractOrigins(flow: WebAuthorizationFlow): Set<String> =
        setOf(flow.signInUri, flow.collectClaimsUri, flow.validateClaimsUri, flow.errorUri)
            .map { uri ->
                buildString {
                    append(uri.scheme).append("://").append(uri.host)
                    if (uri.port != -1) append(":").append(uri.port)
                }
            }
            .toSet()

    override fun doFilter(
        request: HttpRequest<*>,
        chain: ServerFilterChain
    ): Publisher<MutableHttpResponse<*>> {
        val origin = request.headers.origin.getOrNull()
            ?: return chain.proceed(request) // No Origin → not a CORS request

        val allowed = allowedOrigins.contains(origin)

        // Preflight: always short-circuit (before security filter)
        if (request.method == HttpMethod.OPTIONS) {
            val response = HttpResponse.ok<Any>()
            if (allowed) addCorsHeaders(response, origin, preflight = true)
            return Flowable.just(response)
        }

        if (!allowed) return chain.proceed(request)

        return Flowable.fromPublisher(chain.proceed(request)).map { response ->
            addCorsHeaders(response, origin, preflight = false)
            response
        }
    }

    private fun addCorsHeaders(
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
