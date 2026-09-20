package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class FeaturesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledFeaturesConfig(
    /**
     * Allow the end-user to be redirected back to the client application even when none of the requested authorization
     * scopes have been granted.
     */
    val allowAccessToClientWithoutScope: Boolean,
    /**
     * Enable the validation of end-user's email.
     */
    val emailValidation: Boolean,
    /**
     * Automatically grant ALL scopes requested by the client that are not explicitly granted nor declined
     * by any scope granting method.
     *
     * When enabled: Any scope not explicitly granted nor declined is automatically granted.
     * When disabled: Scopes not explicitly granted are rejected (secure default).
     *
     * ⚠️ **UNSAFE - DEVELOPMENT ONLY**
     * This bypasses authorization checks. Never enable in production.
     */
    val grantUnhandledScopes: Boolean,
    /**
     * Print technical details in error messages for debugging purposes.
     */
    val printDetailsInError: Boolean
) : FeaturesConfig()

class DisabledFeaturesConfig(
    configurationErrors: List<ConfigurationException>
) : FeaturesConfig(configurationErrors)

fun FeaturesConfig.orNull(): EnabledFeaturesConfig? {
    return when (this) {
        is EnabledFeaturesConfig -> this
        is DisabledFeaturesConfig -> null
    }
}

fun FeaturesConfig.orThrow(): EnabledFeaturesConfig {
    return when (this) {
        is EnabledFeaturesConfig -> this
        is DisabledFeaturesConfig -> throw this.invalidConfig
    }
}
