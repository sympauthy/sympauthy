package com.sympauthy.config.model

import com.sympauthy.business.model.branding.BrandImage
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.config.exception.ConfigurationException
import java.net.URI

sealed class UIConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledUIConfig(
    val displayName: String?,
    val icon: ConfiguredBrandImage?,
    val logo: ConfiguredBrandImage?,
    val logoDark: ConfiguredBrandImage?,
    val theme: ThemeConfig
) : UIConfig()

class DisabledUIConfig(
    configurationErrors: List<ConfigurationException>
) : UIConfig(configurationErrors)

fun UIConfig.orThrow(): EnabledUIConfig {
    return when (this) {
        is EnabledUIConfig -> this
        is DisabledUIConfig -> throw this.invalidConfig
    }
}

/**
 * An image a deployment configured, by the form its value was written in.
 */
sealed class ConfiguredBrandImage

/**
 * An image read from a file while the configuration was, since a file that is wrong is the file being wrong.
 */
class LocalBrandImage(val image: BrandImage) : ConfiguredBrandImage()

/**
 * An image still to be fetched, since a host that does not answer is the world being wrong.
 */
data class RemoteBrandImage(val uri: URI) : ConfiguredBrandImage()

/**
 * The colours a deployment configured, by role, and whether its pages have a dark theme at all.
 */
data class ThemeConfig(
    val darkMode: DarkMode,
    val light: Map<String, String>,
    val dark: Map<String, String>
) {
    companion object {
        /**
         * The theme of a deployment that configured none.
         */
        val DEFAULT = ThemeConfig(DarkMode.AUTO, emptyMap(), emptyMap())
    }
}

/**
 * The roles a deployment may colour under both `ui.theme.light` and `ui.theme.dark`.
 */
val THEME_ROLES: Set<String> = setOf(
    "primary", "primary-hover", "on-primary", "primary-subtle", "on-primary-subtle", "ground", "surface"
) + listOf(50, 100, 200, 300, 400, 500, 600, 700, 800, 900).map { "ink-$it" }

/**
 * The roles a deployment may colour under `ui.theme.light` alone: what the admin's sidebar reads, and the admin
 * stays light.
 */
val LIGHT_ONLY_THEME_ROLES: Set<String> = setOf(
    "accent", "nav-ground", "nav-text", "nav-muted", "nav-disabled", "nav-hover", "nav-active", "nav-divider"
)
