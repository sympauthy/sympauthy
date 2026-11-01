package com.sympauthy.config.properties

import com.sympauthy.config.properties.ThemeConfigurationProperties.Companion.THEME_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

/**
 * A map rather than a property per role, since `ink-50` is a key no getter hyphenates to.
 */
@ConfigurationProperties(THEME_KEY)
interface ThemeConfigurationProperties {
    @get:Nullable
    val darkMode: String?

    @get:Nullable
    val light: Map<String, String?>?

    @get:Nullable
    val dark: Map<String, String?>?

    companion object {
        const val THEME_KEY = "ui.theme"
    }
}
