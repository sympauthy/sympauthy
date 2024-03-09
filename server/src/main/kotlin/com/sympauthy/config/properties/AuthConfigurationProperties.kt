package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(AUTH_KEY)
interface AuthConfigurationProperties {
    val issuer: String?
    val audience: String?

    companion object {
        const val AUTH_KEY = "auth"
    }
}
