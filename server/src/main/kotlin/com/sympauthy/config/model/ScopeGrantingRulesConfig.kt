package com.sympauthy.config.model

import com.sympauthy.business.model.rule.ScopeGrantingRule
import com.sympauthy.config.exception.ConfigurationException

sealed class ScopeGrantingRulesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledScopeGrantingRulesConfig(
    val userScopeGrantingRules: List<ScopeGrantingRule>,
    val clientScopeGrantingRules: List<ScopeGrantingRule>
) : ScopeGrantingRulesConfig()

class DisabledScopeGrantingRulesConfig(
    configurationErrors: List<ConfigurationException>
) : ScopeGrantingRulesConfig(configurationErrors)

fun ScopeGrantingRulesConfig.orThrow(): EnabledScopeGrantingRulesConfig {
    return when (this) {
        is EnabledScopeGrantingRulesConfig -> this
        is DisabledScopeGrantingRulesConfig -> throw this.invalidConfig
    }
}
