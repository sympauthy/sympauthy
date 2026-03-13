package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(JWT_KEY)
interface JwtConfigurationProperties {
    val publicAlg: String?
    val accessAlg: String?
    val privateAlg: String?

    companion object {
        const val JWT_KEY = "$ADVANCED_KEY.jwt"
    }
}
