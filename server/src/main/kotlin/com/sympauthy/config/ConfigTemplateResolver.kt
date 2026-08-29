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
     * Substitute every `${key}` placeholder in [value] with the entry [context] holds under that
     * key, and return the result. A [value] with no placeholder in it is returned unchanged.
     *
     * A placeholder naming a key [context] does not hold is a mistake in the deployment's file, so
     * it throws a `ConfigurationException` with the code `config.unknown_template`, reported
     * against [configKey] and listing the keys that were available.
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
