package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class ScopesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

class EnabledScopesConfig(
    val scopes: List<ScopeConfig>
) : ScopesConfig()

sealed class ScopeConfig(
    val scope: String
)

class StandardScopeConfig(
    scope: String,
    val enabled: Boolean
): ScopeConfig(scope)

class CustomScopeConfig(
    scope: String,
): ScopeConfig(scope)

class DisabledScopesConfig(
    configurationErrors: List<ConfigurationException>
) : ScopesConfig(configurationErrors)

fun ScopesConfig.orThrow(): EnabledScopesConfig {
    return when (this) {
        is EnabledScopesConfig -> this
        is DisabledScopesConfig -> throw this.invalidConfig
    }
}
