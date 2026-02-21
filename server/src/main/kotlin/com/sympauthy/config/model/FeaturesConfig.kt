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
