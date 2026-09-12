package com.sympauthy.config.properties

import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import com.sympauthy.config.properties.SecurityContextIpConfigurationProperties.Companion.SECURITY_CONTEXT_IP_KEY
import io.micronaut.context.annotation.ConfigurationProperties

/**
 * Which proxy is believed about the address a request came from.
 *
 * With nothing set here the address is the peer of the socket the request arrived on and no
 * forwarded header is read at all. `docs/security.md` carries the trust model: what naming a proxy
 * promises, and why this half is named while the location half may be detected.
 */
@ConfigurationProperties(SECURITY_CONTEXT_IP_KEY)
interface SecurityContextIpConfigurationProperties {

    /**
     * The proxy nearest this server, whose header says where a request came from.
     *
     * One, because only the nearest proxy knows the address as something other than a value it was
     * handed, and because two proxies reading the same forwarded header at different positions would
     * disagree in the caller's favour.
     */
    val provider: String?

    /**
     * A header holding the address, read as it stands, which wins over [provider].
     */
    val header: String?

    companion object {
        const val SECURITY_CONTEXT_IP_KEY = "$SECURITY_CONTEXT_KEY.ip"
    }
}
