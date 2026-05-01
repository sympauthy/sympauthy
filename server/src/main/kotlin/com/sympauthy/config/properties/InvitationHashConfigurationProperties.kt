package com.sympauthy.config.properties

import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import com.sympauthy.config.properties.InvitationHashConfigurationProperties.Companion.INVITATION_HASH_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(INVITATION_HASH_KEY)
interface InvitationHashConfigurationProperties {
    val costParameter: String?
    val blockSize: String?
    val parallelizationParameter: String?
    val keyLength: String?
    val saltLength: String?

    companion object {
        const val INVITATION_HASH_KEY = "$INVITATION_KEY.hash"
    }
}
