package com.sympauthy.config.properties

import com.sympauthy.config.properties.FeaturesConfigurationProperties.Companion.FEATURES_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(FEATURES_KEY)
interface FeaturesConfigurationProperties {
    @get:Nullable
    val allowAccessToClientWithoutScope: String?
    @get:Nullable
    val emailValidation: String?
    @get:Nullable
    val grantUnhandledScopes: String?
    @get:Nullable
    val printDetailsInError: String?

    companion object {
        const val FEATURES_KEY = "features"
    }
}
