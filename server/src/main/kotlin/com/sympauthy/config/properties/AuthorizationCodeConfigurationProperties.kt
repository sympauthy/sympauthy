package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.AuthorizationCodeConfigurationProperties.Companion.AUTHORIZATION_CODE_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(AUTHORIZATION_CODE_KEY)
interface AuthorizationCodeConfigurationProperties {
    val expiration: String?

    companion object {
        const val AUTHORIZATION_CODE_KEY = "$AUTH_KEY.authorization-code"
    }
}