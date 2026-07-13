package com.sympauthy.config.model

import com.sympauthy.business.model.rule.ActAsRule
import com.sympauthy.config.exception.ConfigurationException

sealed class ActAsRulesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledActAsRulesConfig(
    val actAsRules: List<ActAsRule>
) : ActAsRulesConfig()

class DisabledActAsRulesConfig(
    configurationErrors: List<ConfigurationException>
) : ActAsRulesConfig(configurationErrors)

fun ActAsRulesConfig.orThrow(): EnabledActAsRulesConfig {
    return when (this) {
        is EnabledActAsRulesConfig -> this
        is DisabledActAsRulesConfig -> throw this.invalidConfig
    }
}
