package com.sympauthy.config.parsing

import com.sympauthy.business.model.branding.BrandImage
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ConfiguredBrandImage
import com.sympauthy.config.model.LIGHT_ONLY_THEME_ROLES
import com.sympauthy.config.model.LocalBrandImage
import com.sympauthy.config.model.RemoteBrandImage
import com.sympauthy.config.model.THEME_ROLES
import com.sympauthy.config.properties.ThemeConfigurationProperties
import com.sympauthy.config.properties.ThemeConfigurationProperties.Companion.THEME_KEY
import com.sympauthy.config.properties.UIConfigurationProperties
import com.sympauthy.config.properties.UIConfigurationProperties.Companion.UI_KEY
import jakarta.inject.Singleton
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

data class ParsedUIConfig(
    val displayName: String?,
    val icon: ConfiguredBrandImage?,
    val logo: ConfiguredBrandImage?,
    val logoDark: ConfiguredBrandImage?,
    val darkMode: DarkMode?,
    val light: Map<String, String>,
    val dark: Map<String, String>
)

@Singleton
class UIConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: UIConfigurationProperties,
        themeProperties: ThemeConfigurationProperties
    ): ParsedUIConfig {
        val displayName = ctx.parse {
            parser.getString(properties, "$UI_KEY.display-name", UIConfigurationProperties::displayName)
        }
        return ParsedUIConfig(
            displayName = displayName,
            icon = ctx.parse { parseImage(properties, "$UI_KEY.icon", UIConfigurationProperties::icon) },
            logo = ctx.parse { parseImage(properties, "$UI_KEY.logo", UIConfigurationProperties::logo) },
            logoDark = ctx.parse { parseImage(properties, "$UI_KEY.logo-dark", UIConfigurationProperties::logoDark) },
            darkMode = ctx.parse { parseDarkMode(themeProperties) },
            light = parseColors(themeProperties.light),
            dark = parseColors(themeProperties.dark)
        )
    }

    /**
     * YAML reads an unquoted `off` as the boolean false, and that is how the word a deployment was told to write
     * reaches this point, as `no` and `false` do, which cannot be told apart from it. It reads `on` and `yes` as
     * true, a word nobody wrote, so that refusal says what happened rather than name it.
     */
    private fun parseDarkMode(properties: ThemeConfigurationProperties): DarkMode {
        val value = properties.darkMode ?: return DarkMode.AUTO
        return when (value) {
            YAML_OFF -> DarkMode.OFF
            YAML_ON -> throw configExceptionOf("$THEME_KEY.dark-mode", "config.ui.theme.dark_mode_on")
            else -> parser.convertToEnum("$THEME_KEY.dark-mode", value)
        }
    }

    /**
     * The entries naming a role. Which other keys were written, and whether they may be, is read off the file by
     * the validator.
     */
    private fun parseColors(colors: Map<String, String?>?): Map<String, String> = buildMap {
        colors?.forEach { (role, color) ->
            if (color != null && (role in THEME_ROLES || role in LIGHT_ONLY_THEME_ROLES)) put(role, color)
        }
    }

    private fun parseImage(
        properties: UIConfigurationProperties,
        key: String,
        value: (UIConfigurationProperties) -> String?
    ): ConfiguredBrandImage? {
        val source = parser.getString(properties, key, value) ?: return null
        return if (source.startsWith("http://") || source.startsWith("https://")) {
            RemoteBrandImage(parseUrl(key, source))
        } else {
            LocalBrandImage(readImage(key, source))
        }
    }

    /**
     * The URL exactly as it was written, rather than through a builder that encodes it again: a host may sign
     * the query it handed out.
     */
    private fun parseUrl(key: String, source: String): URI {
        val uri = try {
            URI(source)
        } catch (_: URISyntaxException) {
            null
        }
        return uri?.takeUnless { it.host.isNullOrBlank() } ?: throw configExceptionOf(key, "config.invalid_url")
    }

    /**
     * The image in the file at [source]. A scheme other than `http` or `https` reads as a path, and is refused
     * as a file that does not exist.
     *
     * Only a regular file is opened and the read stops past the limit, since a device or a pipe reports no size
     * worth believing and would otherwise hold up the startup reading it.
     */
    private fun readImage(key: String, source: String): BrandImage {
        val path = try {
            Path.of(source)
        } catch (_: InvalidPathException) {
            throw configExceptionOf(key, "config.ui.image.not_found", "path" to source)
        }
        if (!Files.exists(path)) {
            throw configExceptionOf(key, "config.ui.image.not_found", "path" to source)
        }
        if (!Files.isRegularFile(path)) {
            throw configExceptionOf(key, "config.ui.image.unreadable", "path" to source)
        }
        val content = try {
            Files.newInputStream(path).use { it.readNBytes(BrandImage.MAX_SIZE + 1) }
        } catch (_: IOException) {
            throw configExceptionOf(key, "config.ui.image.unreadable", "path" to source)
        }
        if (content.size > BrandImage.MAX_SIZE) {
            throw configExceptionOf(key, "config.ui.image.too_large", "path" to source)
        }
        return BrandImage.ofOrNull(content)
            ?: throw configExceptionOf(key, "config.ui.image.unsupported_type", "path" to source)
    }

    private companion object {
        const val YAML_OFF = "false"
        const val YAML_ON = "true"
    }
}
