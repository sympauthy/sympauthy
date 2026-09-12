package com.sympauthy.config.properties

import com.sympauthy.config.properties.SecurityContextGeoConfigurationProperties.Companion.SECURITY_CONTEXT_GEO_KEY
import com.sympauthy.config.properties.SecurityContextIpConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Which proxies are believed about where the address a request came from is.
 *
 * Several may be named, and they may be detected instead, because every edge publishes its location
 * under a header of its own rather than at a position in one they share. `docs/security.md` says
 * what that buys and what it does not.
 */
@ConfigurationProperties(SECURITY_CONTEXT_GEO_KEY)
interface SecurityContextGeoConfigurationProperties {

    /**
     * Whether every edge publishing a location is read, rather than the ones [providers] names.
     */
    val autoDetect: String?

    /**
     * The edges whose location headers are read, in the order they apply, each overriding the fields
     * the ones before it answered.
     *
     * A word here is the name an edge is published under. An edge publishing no location is not one
     * of them, so naming it is refused at startup rather than configured to no effect.
     */
    val providers: List<String>?

    companion object {
        const val SECURITY_CONTEXT_GEO_KEY = "$SECURITY_CONTEXT_KEY.geo"
    }
}
