package com.sympauthy.config.properties

import com.sympauthy.config.properties.QueueConfigurationProperties.Companion.QUEUE_KEY
import com.sympauthy.config.properties.QueueRetryConfigurationProperties.Companion.QUEUE_RETRY_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(QUEUE_RETRY_KEY)
interface QueueRetryConfigurationProperties {
    @get:Nullable
    val firstDelay: String?
    @get:Nullable
    val maxDelay: String?

    companion object {
        const val QUEUE_RETRY_KEY = "$QUEUE_KEY.retry"
    }
}
