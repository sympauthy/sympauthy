package com.sympauthy.config.properties

import com.sympauthy.config.properties.CorsConfigurationProperties.Companion.CORS_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(CORS_KEY)
interface CorsConfigurationProperties {
    /**
     * Additional header names browsers are allowed to send on cross-origin requests, on top of the headers
     * the application cannot work without.
     *
     * See com.sympauthy.api.filter.CorsPreflightHeaders.MANDATORY_ALLOWED_HEADERS for the headers that are
     * always allowed, whatever this list contains.
     */
    val allowedHeaders: List<String>?

    companion object {
        const val CORS_KEY = "cors"
    }
}
