package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class ProvidersConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

class EnabledProvidersConfig(
    val providers: List<ProviderConfig>
) : ProvidersConfig()

class DisabledProvidersConfig(
    configurationErrors: List<ConfigurationException>
) : ProvidersConfig(configurationErrors)

fun ProvidersConfig.orThrow(): EnabledProvidersConfig {
    return when (this) {
        is EnabledProvidersConfig -> this
        is DisabledProvidersConfig -> throw this.invalidConfig
    }
}

fun ProvidersConfig.getOrNull(): EnabledProvidersConfig? {
    return this as? EnabledProvidersConfig
}
