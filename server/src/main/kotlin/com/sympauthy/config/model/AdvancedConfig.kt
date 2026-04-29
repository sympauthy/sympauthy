package com.sympauthy.config.model

import com.sympauthy.business.manager.jwt.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.config.exception.ConfigurationException
import java.time.Duration

sealed class AdvancedConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledAdvancedConfig(
    val keysGenerationStrategy: CryptoKeysGenerationStrategy,
    val publicJwtAlgorithm: JwtAlgorithm,
    val accessJwtAlgorithm: JwtAlgorithm,
    /**
     * Algorithm used to sign internal JWTs (refresh tokens, provider nonces, state tokens).
     *
     * Must be deterministic (same key + same payload = same signature) because the provider nonce
     * flow reconstructs a JWT at callback time and compares it to the one originally sent to the
     * provider. A non-deterministic algorithm (e.g. ES256, PS256) would produce a different
     * signature each time, causing nonce mismatch errors.
     */
    val privateJwtAlgorithm: JwtAlgorithm,
    val hashConfig: HashConfig,
    val invitationConfig: InvitationAdvancedConfig,
    val validationCode: ValidationCodeConfig,
    val authorizationWebhook: AuthorizationWebhookAdvancedConfig,
) : AdvancedConfig()

class DisabledAdvancedConfig(
    configurationErrors: List<ConfigurationException>
) : AdvancedConfig(configurationErrors)

/**
 * Scrypt parameters for hashing secrets (passwords, invitation tokens) before storing them in the database.
 *
 * Each use case (passwords, invitations) has its own [HashConfig] instance so that the cost parameters
 * can be tuned independently.
 */
data class HashConfig(
    val costParameter: Int,
    val blockSize: Int,
    val parallelizationParameter: Int,
    /**
     * Number of random bytes to generate and then use as a salt for the hashing algorithm.
     */
    val saltLengthInBytes: Int,
    /**
     * Number of bytes generated as an output of the hashing algorithm.
     */
    val keyLengthInBytes: Int,
)

data class ValidationCodeConfig(
    val length: Int,
    val resendDelay: Duration?,
    val expiration: Duration,
)

data class InvitationAdvancedConfig(
    /**
     * Number of random bytes to generate for the invitation token (before base64url encoding).
     */
    val tokenLengthInBytes: Int,
    /**
     * Default validity duration when no explicit expiration is provided.
     */
    val defaultExpiration: Duration,
    /**
     * Maximum allowed validity duration.
     */
    val maxExpiration: Duration,
    /**
     * Scrypt hash configuration for invitation token storage.
     */
    val hashConfig: HashConfig,
)

data class AuthorizationWebhookAdvancedConfig(
    val timeout: Duration,
)

fun AdvancedConfig.orThrow(): EnabledAdvancedConfig {
    return when (this) {
        is EnabledAdvancedConfig -> this
        is DisabledAdvancedConfig -> throw this.invalidConfig
    }
}
