package com.sympauthy.config.properties

import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import com.sympauthy.config.properties.SecurityContextGeoConfigurationProperties.Companion.SECURITY_CONTEXT_GEO_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

/**
 * Which proxies are believed about where the address a request came from is. Several may be named,
 * or detected, and `docs/design/security-context.md` says why.
 */
@ConfigurationProperties(SECURITY_CONTEXT_GEO_KEY)
interface SecurityContextGeoConfigurationProperties {

    /**
     * Whether every edge publishing a location is read, ahead of the ones [providers] names, rather
     * than those alone.
     */
    @get:Nullable
    val autoDetect: String?

    /**
     * The edges whose location headers are read, in the order they apply, each overriding the fields
     * the ones before it answered.
     *
     * A word here is the name an edge publishing a location is published under.
     */
    @get:Nullable
    val providers: List<String>?

    companion object {
        const val SECURITY_CONTEXT_GEO_KEY = "$SECURITY_CONTEXT_KEY.geo"
    }
}
