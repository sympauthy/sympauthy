package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(INVITATION_KEY)
interface InvitationConfigurationProperties {
    val tokenLength: String?
    val defaultExpiration: String?
    val maxExpiration: String?

    companion object {
        const val INVITATION_KEY = "$ADVANCED_KEY.invitation"
    }
}
