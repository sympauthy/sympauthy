package com.sympauthy.config.model

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategyId
import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.SecurityContextField
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
 * Where the security context of a request is read from, and how long the record of one is kept.
 *
 * The two retentions are what makes recording an address defensible: an operator who never looks at
 * this feature still has a policy over the personal data it writes, and the shorter of the two covers
 * the pile they never asked for.
 */
data class SecurityContextConfig(
    /**
     * How what this deployment named under `advanced.security-context.provider` publishes what it
     * saw, resolved to the extraction implementing it: a provider nothing implements is refused
     * before this is built, so nothing downstream looks one up again.
     */
    val profile: EdgeProviderProfile,
    /**
     * The header a field is read from instead of the profile's rule, for the fields a deployment
     * named one for.
     *
     * The value is taken as it stands: an override is a plain read and never a parse, so a header
     * holding a list or a packed set of pairs is recorded whole. It also replaces the profile's rule
     * outright — a named header that does not arrive leaves the field null rather than falling back
     * to the profile.
     */
    val headers: Map<SecurityContextField, String>,
    /**
     * How long a context no user was ever attached to is kept. These come from abandoned flows,
     * failed sign-ins and probing, so they are the shorter-lived of the two.
     */
    val unknownRetention: Duration,
    /**
     * How long a context attached to a user is kept, counted from the last sighting rather than the
     * first: a context is a place someone keeps signing in from, and expiring it from the first
     * would delete a person's home address after six months of using it.
     */
    val knownRetention: Duration,
)

fun AdvancedConfig.orThrow(): EnabledAdvancedConfig {
    return when (this) {
        is EnabledAdvancedConfig -> this
        is DisabledAdvancedConfig -> throw this.invalidConfig
    }
}
