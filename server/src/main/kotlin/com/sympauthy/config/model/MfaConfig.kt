package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class MfaConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledMfaConfig(
    /**
     * Whether TOTP is available as a second authentication factor.
     */
    val totp: Boolean,
    /**
     * Whether MFA is required for all users.
     * When true and no method is enrolled, triggers enrollment.
     */
    val required: Boolean
) : MfaConfig()

class DisabledMfaConfig(
    configurationErrors: List<ConfigurationException>
) : MfaConfig(configurationErrors)

fun MfaConfig.orThrow(): EnabledMfaConfig {
    return when (this) {
        is EnabledMfaConfig -> this
        is DisabledMfaConfig -> throw this.invalidConfig
    }
}
