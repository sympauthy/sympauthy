package com.sympauthy.config.properties

import com.sympauthy.config.properties.UrlsConfigurationProperties.Companion.URLS_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(URLS_KEY)
interface UrlsConfigurationProperties {
    @get:Nullable
    val root: String?

    companion object {
        const val URLS_KEY = "urls"
    }
}
