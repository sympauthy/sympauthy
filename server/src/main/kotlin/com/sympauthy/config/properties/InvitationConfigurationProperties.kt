package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(INVITATION_KEY)
interface InvitationConfigurationProperties {
    @get:Nullable
    val tokenLength: String?
    @get:Nullable
    val defaultExpiration: String?
    @get:Nullable
    val maxExpiration: String?

    companion object {
        const val INVITATION_KEY = "$ADVANCED_KEY.invitation"
    }
}
