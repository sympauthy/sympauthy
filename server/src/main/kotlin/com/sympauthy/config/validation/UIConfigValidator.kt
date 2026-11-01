package com.sympauthy.config.validation

import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.LIGHT_ONLY_THEME_ROLES
import com.sympauthy.config.model.THEME_ROLES
import com.sympauthy.config.parsing.ParsedUIConfig
import com.sympauthy.config.properties.ThemeConfigurationProperties.Companion.THEME_KEY
import jakarta.inject.Singleton

@Singleton
class UIConfigValidator {

    /**
     * [writtenThemeKeys] are the keys the deployment wrote under `ui.theme`, which is what a role is refused
     * by: a key holding no value binds to nothing, and an unquoted `#` starts a comment in YAML, so that is what
     * `primary: #0f766e` is.
     */
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedUIConfig,
        writtenThemeKeys: Set<String>
    ) {
        validateColors(ctx, LIGHT_KEY, parsed.light, THEME_ROLES + LIGHT_ONLY_THEME_ROLES, writtenThemeKeys)
        validateColors(ctx, DARK_KEY, parsed.dark, THEME_ROLES, writtenThemeKeys)
        if (parsed.darkMode == DarkMode.OFF && writtenThemeKeys.any { it == DARK_KEY || it.startsWith("$DARK_KEY.") }) {
            ctx.addError(configExceptionOf(DARK_KEY, "config.ui.theme.dark.dark_mode_off"))
        }
    }

    private fun validateColors(
        ctx: ConfigParsingContext,
        themeKey: String,
        colors: Map<String, String>,
        roles: Set<String>,
        writtenThemeKeys: Set<String>
    ) {
        val writtenRoles = writtenThemeKeys.filter { it.startsWith("$themeKey.") }
            .map { it.removePrefix("$themeKey.") }
        writtenRoles.forEach { role ->
            val key = "$themeKey.$role"
            when {
                role in LIGHT_ONLY_THEME_ROLES && role !in roles -> ctx.addError(
                    configExceptionOf(key, "config.ui.theme.dark.light_only_role", "role" to role)
                )

                role !in roles -> ctx.addError(
                    configExceptionOf(
                        key, "config.ui.theme.unknown_role",
                        "role" to role, "supportedValues" to roles.joinToString(", ")
                    )
                )

                role !in colors -> ctx.addError(configExceptionOf(key, "config.ui.theme.invalid_color"))
            }
        }
        colors.filterKeys { it in roles }.forEach { (role, color) ->
            if (!COLOR.matches(color)) {
                ctx.addError(configExceptionOf("$themeKey.$role", "config.ui.theme.invalid_color"))
            }
        }
    }

    private companion object {
        const val LIGHT_KEY = "$THEME_KEY.light"
        const val DARK_KEY = "$THEME_KEY.dark"

        /**
         * What keeps a value from writing anything into the stylesheet but a colour.
         */
        val COLOR = Regex("#[0-9a-fA-F]{6}")
    }
}
