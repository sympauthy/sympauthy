package com.sympauthy.config.properties

import com.sympauthy.config.properties.MfaConfigurationProperties.Companion.MFA_KEY
import com.sympauthy.config.properties.MfaTotpConfigurationProperties.Companion.MFA_TOTP_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(MFA_TOTP_KEY)
interface MfaTotpConfigurationProperties {
    val enabled: String?

    companion object {
        const val MFA_TOTP_KEY = "$MFA_KEY.totp"
    }
}
