package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.TokenConfigurationProperties.Companion.TOKEN_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(TOKEN_KEY)
interface TokenConfigurationProperties {
    val accessExpiration: String?
    val idExpiration: String?
    val refreshEnabled: String?
    val refreshExpiration: String?
    val dpopRequired: String?

    companion object {
        const val TOKEN_KEY = "$AUTH_KEY.token"
    }
}
