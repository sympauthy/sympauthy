package com.sympauthy.config.properties

import com.sympauthy.config.properties.MfaConfigurationProperties.Companion.MFA_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(MFA_KEY)
interface MfaConfigurationProperties {
    @get:Nullable
    val required: String?

    companion object {
        const val MFA_KEY = "mfa"
    }
}
