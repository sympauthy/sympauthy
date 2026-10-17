package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(AUTH_KEY)
interface AuthConfigurationProperties {
    @get:Nullable
    val issuer: String?
    @get:Nullable
    val identifierClaims: List<String>?
    @get:Nullable
    val userMergingEnabled: Boolean?

    companion object {
        const val AUTH_KEY = "auth"
    }
}
