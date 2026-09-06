package com.sympauthy.config.properties

import com.sympauthy.config.properties.AccessReviewWebhookConfigurationProperties.Companion.ACCESS_REVIEW_WEBHOOK_KEY
import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(ACCESS_REVIEW_WEBHOOK_KEY)
interface AccessReviewWebhookConfigurationProperties {
    val timeout: String?
    val pastContexts: String?

    companion object {
        const val ACCESS_REVIEW_WEBHOOK_KEY = "$ADVANCED_KEY.webhooks.access-review"
    }
}
