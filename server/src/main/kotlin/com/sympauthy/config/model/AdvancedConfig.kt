package com.sympauthy.config.model

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategyId
import com.sympauthy.config.exception.ConfigurationException
import java.time.Duration

sealed class AdvancedConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledAdvancedConfig(
    val keysGenerationStrategyId: CryptoKeysGenerationStrategyId,
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
    val pagination: PaginationConfig,
    val cleanup: CleanupConfig,
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

/**
 * Bounds every paged endpoint applies to the page and size query parameters a caller sends.
 *
 * The maximum is what stops a caller asking for a whole collection in one response, so it is a
 * deployment's to raise or lower against the size of the collections it actually holds.
 */
data class PaginationConfig(
    /**
     * Number of items returned when the caller sends no size.
     */
    val defaultSize: Int,
    /**
     * Largest size a caller may ask for. A larger one is refused rather than reduced.
     */
    val maxSize: Int,
)

/**
 * Bounds one run of the scheduled cleanup that collects expired interactive flow sessions and the
 * accounts an abandoned sign-up left behind.
 *
 * The cleanup is self-correcting — what a run leaves behind the next one takes — so the bound is what
 * a deployment raises when its backlog stops draining, and lowers when the locks a run holds are felt
 * elsewhere.
 */
data class CleanupConfig(
    /**
     * Largest number of rows one run takes: the expired sessions the session cleanup deletes, and the
     * abandoned accounts the sweep collects, each bounded on its own.
     */
    val batchSize: Int,
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
