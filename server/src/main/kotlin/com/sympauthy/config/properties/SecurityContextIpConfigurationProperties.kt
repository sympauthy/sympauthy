package com.sympauthy.config.properties

import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import com.sympauthy.config.properties.SecurityContextIpConfigurationProperties.Companion.SECURITY_CONTEXT_IP_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

/**
 * Which proxy is believed about the address a request came from. `docs/design/security-context.md`
 * carries the trust model, and what is believed where nothing is set here.
 */
@ConfigurationProperties(SECURITY_CONTEXT_IP_KEY)
interface SecurityContextIpConfigurationProperties {

    /**
     * The proxy nearest this server, whose header says where a request came from.
     */
    @get:Nullable
    val provider: String?

    /**
     * A header holding the address, read as it stands, which wins over [provider].
     */
    @get:Nullable
    val header: String?

    companion object {
        const val SECURITY_CONTEXT_IP_KEY = "$SECURITY_CONTEXT_KEY.ip"
    }
}
