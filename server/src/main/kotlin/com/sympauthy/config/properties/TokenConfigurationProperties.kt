package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.TokenConfigurationProperties.Companion.TOKEN_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(TOKEN_KEY)
interface TokenConfigurationProperties {
    @get:Nullable
    val accessExpiration: String?
    @get:Nullable
    val idExpiration: String?
    @get:Nullable
    val refreshEnabled: String?
    @get:Nullable
    val refreshExpiration: String?
    @get:Nullable
    val dpopRequired: String?

    companion object {
        const val TOKEN_KEY = "$AUTH_KEY.token"
    }
}
