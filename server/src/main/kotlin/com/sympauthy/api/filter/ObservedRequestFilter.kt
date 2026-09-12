package com.sympauthy.api.filter

import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.util.SecurityContextUtil
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.filter.ServerFilterPhase
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Reads where every request came from, once, and leaves it on the request under [OBSERVED_REQUEST]
 * for whatever handles it.
 *
 * **It runs before Micronaut Security**, because an address that exists only once a caller has been
 * authenticated is no use to anything deciding whether to answer them at all. Throttling a credential
 * check is that: it has to decide, on the most exposed endpoint in this server, about a caller who has
 * presented nothing yet and may never.
 *
 * **It is the only filter here not scoped to a surface**, and deliberately: a filter naming the paths
 * it covers is the routing table written a second time, and the second copy is the one that goes
 * stale. What it costs everywhere else is a pair of header reads —
 * [SecurityContextUtil.observe] short-circuits the location entirely under the shipped configuration
 * — and no I/O at all.
 *
 * **It computes and stores nothing else.** A row per request would make a record of every asset and
 * every health probe, so which requests are worth one is decided by the handlers that know what a
 * request meant — and they take what is left here as an ordinary parameter, so no manager reaches back
 * for it.
 */
@Singleton
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
class ObservedRequestFilter(
    @Inject private val securityContextUtil: SecurityContextUtil
) : Ordered {

    /**
     * At [ServerFilterPhase.FIRST], which is after the CORS filters answering a preflight at
     * `FIRST.before()` and ahead of the security filter. A preflight this never sees carries no
     * credential and reaches no handler.
     */
    override fun getOrder(): Int = ServerFilterPhase.FIRST.order()

    @RequestFilter
    fun observe(request: HttpRequest<*>) {
        request.setAttribute(OBSERVED_REQUEST, securityContextUtil.observe(request))
    }

    companion object {

        /**
         * The attribute a handler binds with `@RequestAttribute` to be given the
         * [com.sympauthy.business.model.security.ObservedRequest] this filter read.
         */
        const val OBSERVED_REQUEST = "sympauthy.observed-request"
    }
}
