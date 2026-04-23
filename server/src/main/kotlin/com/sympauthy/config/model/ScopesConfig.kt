package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class ScopesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledScopesConfig(
    val scopes: List<ScopeConfig>
) : ScopesConfig()

sealed class ScopeConfig(
    val scope: String,
    val audienceId: String?
)

class OpenIdConnectScopeConfig(
    scope: String,
    val enabled: Boolean,
    audienceId: String? = null
) : ScopeConfig(scope, audienceId)

class CustomScopeConfig(
    scope: String,
    val consentable: Boolean,
    audienceId: String? = null
) : ScopeConfig(scope, audienceId)

class DisabledScopesConfig(
    configurationErrors: List<ConfigurationException>
) : ScopesConfig(configurationErrors)

fun ScopesConfig.orThrow(): EnabledScopesConfig {
    return when (this) {
        is EnabledScopesConfig -> this
        is DisabledScopesConfig -> throw this.invalidConfig
    }
}

fun ScopesConfig.orNull(): EnabledScopesConfig? {
    return this as? EnabledScopesConfig
}
