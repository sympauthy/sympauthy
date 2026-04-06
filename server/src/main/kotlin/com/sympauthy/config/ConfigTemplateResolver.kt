package com.sympauthy.config

import com.sympauthy.config.exception.configExceptionOf
import jakarta.inject.Singleton

/**
 * Resolves `${key}` placeholders in configuration strings using a provided context map.
 * Uses FreeMarker-style syntax for consistency with other templating in the project.
 */
@Singleton
class ConfigTemplateResolver {

    private val templateRegex = Regex("""\$\{([^}]+)}""")

    /**
     * Resolve all `${key}` placeholders in [value] using the provided [context] map.
     *
     * @param value The string potentially containing `${key}` placeholders.
     * @param context A map of available template variables and their values.
     * @param configKey The configuration key, used for error reporting.
     * @throws com.sympauthy.config.exception.ConfigurationException if a placeholder references an unknown key.
     */
    fun resolve(value: String, context: Map<String, String>, configKey: String): String {
        return templateRegex.replace(value) { match ->
            val key = match.groupValues[1]
            context[key] ?: throw configExceptionOf(
                configKey, "config.unknown_template",
                "template" to key,
                "knownTemplates" to context.keys.joinToString(", ")
            )
        }
    }
}
