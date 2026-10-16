package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties.Companion.AUTHORIZATION_WEBHOOK_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(AUTHORIZATION_WEBHOOK_KEY)
interface AuthorizationWebhookConfigurationProperties {
    @get:Nullable
    val timeout: String?

    companion object {
        const val AUTHORIZATION_WEBHOOK_KEY = "$ADVANCED_KEY.webhooks.authorization"
    }
}
