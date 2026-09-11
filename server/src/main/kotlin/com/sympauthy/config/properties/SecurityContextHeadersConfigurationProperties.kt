package com.sympauthy.config.properties

import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import com.sympauthy.config.properties.SecurityContextHeadersConfigurationProperties.Companion.HEADERS_KEY
import io.micronaut.context.annotation.ConfigurationProperties

/**
 * The header one field is read from, where a deployment's edge puts it somewhere no provider knows
 * to look.
 *
 * The fields are declared rather than left a map so that a name none of them matches is refused as a
 * key binding to nothing. What a value named here means is `docs/security.md`.
 */
@ConfigurationProperties(HEADERS_KEY)
interface SecurityContextHeadersConfigurationProperties {
    val clientIp: String?
    val countryCode: String?
    val regionCode: String?
    val region: String?
    val city: String?
    val postalCode: String?
    val timeZone: String?

    companion object {
        const val HEADERS_KEY = "$SECURITY_CONTEXT_KEY.headers"
    }
}
