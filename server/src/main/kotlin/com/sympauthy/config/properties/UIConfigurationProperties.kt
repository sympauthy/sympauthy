package com.sympauthy.config.properties

import com.sympauthy.config.properties.UIConfigurationProperties.Companion.UI_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(UI_KEY)
interface UIConfigurationProperties {
    @get:Nullable
    val displayName: String?

    companion object {
        const val UI_KEY = "ui"
    }
}
