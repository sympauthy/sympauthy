package com.sympauthy.config.model

import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.exception.ConfigurationException

sealed class ScopesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledScopesConfig(
    /**
     * Every scope this authorization server knows about: the ones the deployment defined, the
     * built-in ones the server contributes itself, and the ones the deployment turned off.
     */
    val scopes: List<Scope>
) : ScopesConfig() {

    /**
     * The half of [scopes] this server serves, which is what everything but the administration API
     * reads.
     */
    val enabledScopes: List<EnabledScope> = scopes.filterIsInstance<EnabledScope>()
}

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
