package com.sympauthy.config.properties

import com.sympauthy.config.properties.MfaConfigurationProperties.Companion.MFA_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(MFA_KEY)
interface MfaConfigurationProperties {
    val required: String?

    companion object {
        const val MFA_KEY = "mfa"
    }
}
