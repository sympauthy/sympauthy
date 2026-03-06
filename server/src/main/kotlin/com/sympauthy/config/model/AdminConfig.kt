package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class AdminConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledAdminConfig(
    val enabled: Boolean,
    val integratedUi: Boolean
) : AdminConfig()

class DisabledAdminConfig(
    configurationErrors: List<ConfigurationException>
) : AdminConfig(configurationErrors)

fun AdminConfig.orThrow(): EnabledAdminConfig {
    return when (this) {
        is EnabledAdminConfig -> this
        is DisabledAdminConfig -> throw this.invalidConfig
    }
}
