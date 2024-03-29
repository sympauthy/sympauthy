package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class AuthConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

class EnabledAuthConfig(
    val issuer: String,
    val audience: String?,
    val token: TokenConfig
) : AuthConfig()

class DisabledAuthConfig(
    configurationErrors: List<ConfigurationException>
) : AuthConfig(configurationErrors)

fun AuthConfig.orThrow(): EnabledAuthConfig {
    return when (this) {
        is EnabledAuthConfig -> this
        is DisabledAuthConfig -> throw this.invalidConfig
    }
}
