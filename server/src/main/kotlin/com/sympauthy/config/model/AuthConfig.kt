package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class AuthConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledAuthConfig(
    val issuer: String,
    val token: TokenConfig,
    val authorizationCode: AuthorizationCodeConfig,
    /**
     * List of claim IDs that uniquely identify a user.
     * Used as login claims for password sign-in and as merging keys for provider-based authentication.
     */
    val identifierClaims: List<String>,
    val userMergingEnabled: Boolean,
    val byPassword: ByPasswordConfig
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
