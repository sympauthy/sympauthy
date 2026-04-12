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
    val privateJwtAlgorithm: JwtAlgorithm,
    val hashConfig: HashConfig,
    val validationCode: ValidationCodeConfig,
    val authorizationWebhook: AuthorizationWebhookAdvancedConfig,
) : AdvancedConfig()

class DisabledAdvancedConfig(
    configurationErrors: List<ConfigurationException>
) : AdvancedConfig(configurationErrors)

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

data class AuthorizationWebhookAdvancedConfig(
    val timeout: Duration,
)

fun AdvancedConfig.orThrow(): EnabledAdvancedConfig {
    return when (this) {
        is EnabledAdvancedConfig -> this
        is DisabledAdvancedConfig -> throw this.invalidConfig
    }
}
