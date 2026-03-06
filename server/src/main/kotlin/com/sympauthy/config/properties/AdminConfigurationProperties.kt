package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdminConfigurationProperties.Companion.ADMIN_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(ADMIN_KEY)
interface AdminConfigurationProperties {
    val enabled: String?
    val integratedUi: String?

    companion object {
        const val ADMIN_KEY = "admin"
    }
}
