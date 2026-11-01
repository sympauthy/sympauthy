package com.sympauthy.config.properties

import com.sympauthy.config.properties.UIConfigurationProperties.Companion.UI_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(UI_KEY)
interface UIConfigurationProperties {
    @get:Nullable
    val displayName: String?

    /**
     * The favicon and small mark: an `http://` or `https://` URL, or a file path.
     */
    @get:Nullable
    val icon: String?

    /**
     * The logo for light grounds: an `http://` or `https://` URL, or a file path.
     */
    @get:Nullable
    val logo: String?

    /**
     * The logo for dark grounds: an `http://` or `https://` URL, or a file path.
     */
    @get:Nullable
    val logoDark: String?

    companion object {
        const val UI_KEY = "ui"
    }
}
