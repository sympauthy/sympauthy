package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import io.micronaut.context.annotation.ConfigurationProperties

/**
 * What this server does about where a request came from: how it reads it, under
 * [SecurityContextIpConfigurationProperties] and [SecurityContextGeoConfigurationProperties], and how
 * long it keeps what it read.
 *
 * The prefix is declared here rather than apart from all three because there is now a setting directly
 * under it, so the domain has an owner where it used to have only children.
 */
@ConfigurationProperties(SECURITY_CONTEXT_KEY)
interface SecurityContextConfigurationProperties {

    /**
     * How long a place a known person signs in from is kept, measured from when it was last seen.
     *
     * There is no unknown-user twin. Nothing unidentified is stored in that table — an observation with
     * no person yet is attached to the interactive flow session that made it, and is collected with it —
     * so there is no second population to name a second number for.
     */
    val knownUserRetention: String?

    companion object {
        const val SECURITY_CONTEXT_KEY = "$ADVANCED_KEY.security-context"
    }
}
