package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdminConfigurationProperties.Companion.ADMIN_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(ADMIN_KEY)
interface AdminConfigurationProperties {
    @get:Nullable
    val enabled: String?
    @get:Nullable
    val integratedUi: String?
    @get:Nullable
    val audience: String?

    companion object {
        const val ADMIN_KEY = "admin"
    }
}
