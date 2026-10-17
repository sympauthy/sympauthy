package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(HASH_KEY)
interface HashConfigurationProperties {
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
        const val HASH_KEY = "$ADVANCED_KEY.hash"
    }
}
