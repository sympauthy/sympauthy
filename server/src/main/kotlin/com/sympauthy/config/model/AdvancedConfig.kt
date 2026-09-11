package com.sympauthy.config.model

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.config.exception.ConfigurationException
import java.time.Duration

sealed class AdvancedConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledAdvancedConfig(
    val keysGenerationStrategy: ConfiguredImplementation<CryptoKeysGenerationStrategy>,
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
    val securityContext: SecurityContextConfig,
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

data class AuthorizationWebhookAdvancedConfig(
    val timeout: Duration,
)

/**
 * Which edges this deployment sits behind, and are therefore believed about where a request came
 * from.
 *
 * The trustless configuration is the empty one — no provider, no auto-detection, no override — and
 * it is what a deployment that configured nothing has. Every header this server reads is read
 * because something here named it.
 */
data class SecurityContextConfig(
    /**
     * Whether every published provider is applied, sorted by the name each is published under,
     * rather than only the ones [providers] names.
     */
    val autoDetect: Boolean,
    /**
     * The edges in front of this server, in the order an operator wrote them, each overriding the
     * fields the ones before it answered.
     */
    val providers: List<ConfiguredImplementation<EdgeProvider>>,
    /**
     * The headers a deployment named for itself, which win over every provider.
     */
    val headers: SecurityContextHeadersConfig,
)

/**
 * The header each field is read from where a deployment named one, and null where the providers
 * answer for it.
 */
data class SecurityContextHeadersConfig(
    val clientIp: String?,
    val countryCode: String?,
    val regionCode: String?,
    val region: String?,
    val city: String?,
    val postalCode: String?,
    val timeZone: String?,
)

fun AdvancedConfig.orThrow(): EnabledAdvancedConfig {
    return when (this) {
        is EnabledAdvancedConfig -> this
        is DisabledAdvancedConfig -> throw this.invalidConfig
    }
}
