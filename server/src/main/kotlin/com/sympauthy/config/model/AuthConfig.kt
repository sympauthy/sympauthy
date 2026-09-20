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
     * The claim IDs that identify an account, any one of which does so on its own: a value one of them
     * holds belongs to a single account across all of them rather than within one claim.
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
