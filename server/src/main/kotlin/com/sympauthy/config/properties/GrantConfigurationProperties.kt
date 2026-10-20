package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.GrantConfigurationProperties.Companion.GRANT_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(GRANT_KEY)
interface GrantConfigurationProperties {
    @get:Nullable
    val expiration: String?

    companion object {
        const val GRANT_KEY = "$AUTH_KEY.grant"
    }
}
