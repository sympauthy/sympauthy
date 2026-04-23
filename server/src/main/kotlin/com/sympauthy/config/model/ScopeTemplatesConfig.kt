package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class ScopeTemplatesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledScopeTemplatesConfig(
    val templates: Map<String, ScopeTemplate>
) : ScopeTemplatesConfig()

class DisabledScopeTemplatesConfig(
    configurationErrors: List<ConfigurationException>
) : ScopeTemplatesConfig(configurationErrors)

fun ScopeTemplatesConfig.orThrow(): EnabledScopeTemplatesConfig {
    return when (this) {
        is EnabledScopeTemplatesConfig -> this
        is DisabledScopeTemplatesConfig -> throw this.invalidConfig
    }
}

fun ScopeTemplatesConfig.orNull(): EnabledScopeTemplatesConfig? {
    return this as? EnabledScopeTemplatesConfig
}

/**
 * A validated scope template holding default values for scope configurations.
 *
 * All properties are nullable since a template only needs to define the values it wants to provide as defaults.
 */
data class ScopeTemplate(
    val id: String,
    val enabled: Boolean?,
    val type: String?,
    val audience: String?
)
