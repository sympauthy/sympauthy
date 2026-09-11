package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Which proxies sit in front of this server, and are therefore believed about where a request came
 * from.
 *
 * `docs/security.md` carries the trust model: what naming one promises, what a deployment whose
 * origin is reachable without going through one is exposed to, and how several are merged.
 */
@ConfigurationProperties(SECURITY_CONTEXT_KEY)
interface SecurityContextConfigurationProperties {

    /**
     * Whether every provider this server publishes is applied, rather than the ones [providers]
     * names.
     */
    val autoDetect: String?

    /**
     * The proxies in front of this server, in the order they apply.
     *
     * A word here is the name a provider is published under, so one naming none of them takes
     * readiness down at startup against the position it was written at.
     */
    val providers: List<String>?

    companion object {
        const val SECURITY_CONTEXT_KEY = "$ADVANCED_KEY.security-context"
    }
}
