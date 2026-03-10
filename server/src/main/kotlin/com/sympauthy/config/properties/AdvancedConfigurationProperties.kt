package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(ADVANCED_KEY)
interface AdvancedConfigurationProperties {
    val keysGenerationStrategy: String?

    companion object {
        const val ADVANCED_KEY = "advanced"
    }
}
