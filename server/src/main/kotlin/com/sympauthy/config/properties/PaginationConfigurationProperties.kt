package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.PaginationConfigurationProperties.Companion.PAGINATION_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(PAGINATION_KEY)
interface PaginationConfigurationProperties {
    @get:Nullable
    val defaultSize: String?
    @get:Nullable
    val maxSize: String?

    companion object {
        const val PAGINATION_KEY = "$ADVANCED_KEY.pagination"
    }
}
