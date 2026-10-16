package com.sympauthy.config.properties

import com.sympauthy.config.properties.SecurityContextGeoConfigurationProperties.Companion.SECURITY_CONTEXT_GEO_KEY
import com.sympauthy.config.properties.SecurityContextGeoHeadersConfigurationProperties.Companion.GEO_HEADERS_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

/**
 * The header one location field is read from, where a deployment's edge puts it somewhere no edge
 * published here knows to look.
 *
 * The fields are declared rather than left a map so that a name none of them matches is refused as a
 * key binding to nothing. What a value named here means is `docs/design/security-context.md`.
 */
@ConfigurationProperties(GEO_HEADERS_KEY)
interface SecurityContextGeoHeadersConfigurationProperties {
    @get:Nullable
    val countryCode: String?
    @get:Nullable
    val regionCode: String?
    @get:Nullable
    val region: String?
    @get:Nullable
    val city: String?
    @get:Nullable
    val postalCode: String?
    @get:Nullable
    val timeZone: String?

    companion object {
        const val GEO_HEADERS_KEY = "$SECURITY_CONTEXT_GEO_KEY.headers"
    }
}
