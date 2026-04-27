package com.sympauthy.config.properties

import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(JWT_KEY)
interface JwtConfigurationProperties {
    val publicAlg: String?
    val accessAlg: String?

    /**
     * Algorithm used to sign internal JWTs (refresh tokens, provider nonces, state tokens).
     *
     * Must be deterministic (same key + same payload = same signature) because the provider nonce
     * flow reconstructs a JWT at callback time and compares it to the one originally sent to the
     * provider. A non-deterministic algorithm (e.g. ES256, PS256) would produce a different
     * signature each time, causing nonce mismatch errors.
     */
    val privateAlg: String?

    companion object {
        const val JWT_KEY = "$ADVANCED_KEY.jwt"
    }
}
