package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.ByPasswordConfigurationProperties.Companion.BY_PASSWORD_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(BY_PASSWORD_KEY)
interface ByPasswordConfigurationProperties {
    val enabled: String?

    companion object {
        const val BY_PASSWORD_KEY = "$AUTH_KEY.by-password"
    }
}
