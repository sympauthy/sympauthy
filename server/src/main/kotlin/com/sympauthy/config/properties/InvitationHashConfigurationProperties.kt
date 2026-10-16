package com.sympauthy.config.properties

import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import com.sympauthy.config.properties.InvitationHashConfigurationProperties.Companion.INVITATION_HASH_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(INVITATION_HASH_KEY)
interface InvitationHashConfigurationProperties {
    @get:Nullable
    val costParameter: String?
    @get:Nullable
    val blockSize: String?
    @get:Nullable
    val parallelizationParameter: String?
    @get:Nullable
    val keyLength: String?
    @get:Nullable
    val saltLength: String?

    companion object {
        const val INVITATION_HASH_KEY = "$INVITATION_KEY.hash"
    }
}
