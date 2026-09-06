package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(SECURITY_CONTEXT_KEY)
interface SecurityContextConfigurationProperties {
    val provider: String?
    val headers: Map<String, String>?
    val unknownRetention: String?
    val knownRetention: String?

    companion object {
        const val SECURITY_CONTEXT_KEY = "$ADVANCED_KEY.security-context"
    }
}
