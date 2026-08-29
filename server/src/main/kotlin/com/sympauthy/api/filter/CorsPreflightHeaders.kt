package com.sympauthy.api.filter

import com.sympauthy.config.model.CorsConfig
import com.sympauthy.config.model.orNull
import io.micronaut.http.HttpHeaders
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Single source of truth for the headers [FlowCorsFilter], [AdminCorsFilter] and [WildcardCorsFilter] add
 * to an `OPTIONS` preflight response.
 *
 * The three filters differ in how they resolve the allowed origin and in the HTTP methods they advertise,
 * but they share one `Access-Control-Allow-Headers` value and one `Access-Control-Max-Age`. Centralising
 * them here means `cors.allowed-headers` is read in exactly one place.
 *
 * If the `cors` configuration section is invalid, only [MANDATORY_ALLOWED_HEADERS] is advertised: a
 * configuration mistake must never break CORS for the endpoints the application itself depends on. The
 * errors are reported by [com.sympauthy.business.manager.ConfigReadinessManager] instead.
 */
@Singleton
class CorsPreflightHeaders(
    @Inject private val corsConfig: CorsConfig
) {

    /**
     * Value of the `Access-Control-Allow-Headers` response header.
     *
     * Merged once, on first use. [corsConfig] is a constructor dependency and is therefore already
     * resolved by then: this only defers building the string, not reading the configuration.
     */
    val allowedHeaders: String by lazy {
        mergeAllowedHeaders(corsConfig.orNull()?.allowedHeaders ?: emptyList())
    }

    /**
     * Add the headers that are only meaningful on a preflight response.
     *
     * [allowedMethods] is left to the caller: the tiers legitimately advertise different methods.
     */
    fun addTo(response: MutableHttpResponse<*>, allowedMethods: String) {
        response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowedMethods)
        response.headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaders)
        response.headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, MAX_AGE)
    }

    companion object {
        private const val MAX_AGE = "86400"

        /**
         * Headers the application cannot work without. They are always advertised, whatever
         * `cors.allowed-headers` contains, and are deliberately not configurable:
         * - `Content-Type` — every JSON and form-encoded request body.
         * - `Authorization` — bearer and state tokens.
         * - `DPoP` — sender-constrained tokens, see
         *   [RFC 9449](https://datatracker.ietf.org/doc/html/rfc9449#section-5).
         */
        val MANDATORY_ALLOWED_HEADERS = listOf("Content-Type", "Authorization", "DPoP")

        /**
         * Build the `Access-Control-Allow-Headers` value: the mandatory headers first, then the
         * [configured] ones.
         *
         * Header names are matched case-insensitively per the Fetch specification, so entries are
         * deduplicated on their lowercase form while keeping a stable order and the canonical casing of
         * the mandatory entries.
         */
        fun mergeAllowedHeaders(configured: List<String>): String {
            val headersByLowercaseName = LinkedHashMap<String, String>()
            for (header in MANDATORY_ALLOWED_HEADERS + configured) {
                val name = header.trim()
                if (name.isNotEmpty()) {
                    headersByLowercaseName.putIfAbsent(name.lowercase(), name)
                }
            }
            return headersByLowercaseName.values.joinToString(", ")
        }
    }
}
