package com.sympauthy.config.properties

import com.sympauthy.config.properties.AudienceConfigurationProperties.Companion.AUDIENCES_KEY
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * Configuration of an audience that groups client applications sharing consent, scopes, and claims.
 */
@EachProperty(AUDIENCES_KEY)
class AudienceConfigurationProperties(
    @param:Parameter val id: String
) {
    var tokenAudience: String? = null

    companion object {
        const val AUDIENCES_KEY = "audiences"
    }
}
