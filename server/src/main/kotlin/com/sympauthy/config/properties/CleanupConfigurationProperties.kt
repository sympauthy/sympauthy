package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.CleanupConfigurationProperties.Companion.CLEANUP_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(CLEANUP_KEY)
interface CleanupConfigurationProperties {
    val batchSize: String?

    companion object {
        const val CLEANUP_KEY = "$ADVANCED_KEY.cleanup"
    }
}
