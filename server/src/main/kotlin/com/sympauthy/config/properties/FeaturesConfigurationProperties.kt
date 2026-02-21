package com.sympauthy.config.properties

import com.sympauthy.config.properties.FeaturesConfigurationProperties.Companion.FEATURES_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(FEATURES_KEY)
interface FeaturesConfigurationProperties {
    val allowAccessToClientWithoutScope: String?
    val emailValidation: String?
    val printDetailsInError: String?

    companion object {
        const val FEATURES_KEY = "features"
    }
}
