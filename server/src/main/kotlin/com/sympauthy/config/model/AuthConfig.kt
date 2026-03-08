package com.sympauthy.config.model

import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.config.exception.ConfigurationException

sealed class AuthConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledAuthConfig(
    val issuer: String,
    val audience: String?,
    val token: TokenConfig,
    /**
     * List of [OpenIdClaim] that the user can use as a login to sign-in.
     */
    val loginClaims: List<OpenIdClaim>,
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
