package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.QueueConfigurationProperties.Companion.QUEUE_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(QUEUE_KEY)
interface QueueConfigurationProperties {
    @get:Nullable
    val pollInterval: String?
    @get:Nullable
    val batchSize: String?
    @get:Nullable
    val shutdownGrace: String?

    companion object {
        const val QUEUE_KEY = "$ADVANCED_KEY.queue"
    }
}
