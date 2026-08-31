package com.sympauthy.config.model

import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.exception.ConfigurationException

sealed class ScopesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledScopesConfig(
    /**
     * Every scope this authorization server serves: the ones the deployment defined, and the
     * built-in ones the server contributes itself.
     */
    val scopes: List<EnabledScope>
) : ScopesConfig()

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
