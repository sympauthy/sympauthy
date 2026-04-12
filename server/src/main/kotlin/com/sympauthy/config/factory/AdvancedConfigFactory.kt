package com.sympauthy.config.factory

import com.sympauthy.business.manager.jwt.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.AdvancedConfigurationProperties
import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.HashConfigurationProperties
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties.Companion.AUTHORIZATION_WEBHOOK_KEY
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties.Companion.VALIDATION_CODE_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AdvancedConfigFactory(
    @Inject private val parser: ConfigParser,
) {

    @Singleton
    fun provideConfig(
        properties: AdvancedConfigurationProperties,
        jwtProperties: JwtConfigurationProperties,
        hashProperties: HashConfigurationProperties,
        validationCodeProperties: ValidationCodeConfigurationProperties,
        authorizationWebhookProperties: AuthorizationWebhookConfigurationProperties,
        keyGenerationStrategies: Map<String, CryptoKeysGenerationStrategy>,
    ): AdvancedConfig {
        val errors = mutableListOf<ConfigurationException>()

        val keysGenerationStrategy = try {
            getKeysGenerationStrategy(properties, keyGenerationStrategies)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val publicJwtAlgorithm = try {
            getPublicKeyAlgorithm(jwtProperties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val accessJwtAlgorithm = try {
            getAccessKeyAlgorithm(jwtProperties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val privateJwtAlgorithm = try {
            parser.getEnumOrThrow<JwtConfigurationProperties, JwtAlgorithm>(
                jwtProperties, "$JWT_KEY.private-alg",
                JwtConfigurationProperties::privateAlg
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val (hashConfig, hashErrors) = getHashConfig(hashProperties)
        errors.addAll(hashErrors)

        val (validationCodeConfig, validationCodeErrors) = getValidationCodeConfig(validationCodeProperties)
        errors.addAll(validationCodeErrors)

        val authorizationWebhookConfig = try {
            getAuthorizationWebhookConfig(authorizationWebhookProperties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            return EnabledAdvancedConfig(
                keysGenerationStrategy = keysGenerationStrategy!!,
                publicJwtAlgorithm = publicJwtAlgorithm!!,
                accessJwtAlgorithm = accessJwtAlgorithm!!,
                privateJwtAlgorithm = privateJwtAlgorithm!!,
                hashConfig = hashConfig!!,
                validationCode = validationCodeConfig!!,
                authorizationWebhook = authorizationWebhookConfig!!,
            )
        } else {
            DisabledAdvancedConfig(errors)
        }
    }

    private fun getKeysGenerationStrategy(
        properties: AdvancedConfigurationProperties,
        keyGenerationStategies: Map<String, CryptoKeysGenerationStrategy>
    ): CryptoKeysGenerationStrategy {
        val strategyId = parser.getString(
            properties, "$ADVANCED_KEY.keys-generation-strategy",
            AdvancedConfigurationProperties::keysGenerationStrategy
        )
        return strategyId?.let { keyGenerationStategies[it] } ?: throw configExceptionOf(
            "$ADVANCED_KEY.keys-generation-strategy",
            "config.advanced.generation_algorithm.invalid",
            "algorithm" to strategyId,
            "algorithms" to keyGenerationStategies.keys.joinToString(", ")
        )
    }

    private fun getPublicKeyAlgorithm(
        properties: JwtConfigurationProperties
    ): JwtAlgorithm {
        val publicJwtAlgorithm: JwtAlgorithm = parser.getEnumOrThrow(
            properties, "$JWT_KEY.public-alg",
            JwtConfigurationProperties::publicAlg
        )
        if (!publicJwtAlgorithm.keyAlgorithm.supportsPublicKey) {
            throw configExceptionOf(
                "$JWT_KEY.public-alg",
                "config.advanced.jwt.public_alg.unsupported_public_key",
                "algorithms" to JwtAlgorithm.entries
                    .filter { it.keyAlgorithm.supportsPublicKey }
                    .joinToString(", ")
            )
        }
        return publicJwtAlgorithm
    }

    private fun getAccessKeyAlgorithm(
        properties: JwtConfigurationProperties
    ): JwtAlgorithm {
        val accessJwtAlgorithm: JwtAlgorithm = parser.getEnumOrThrow(
            properties, "$JWT_KEY.access-alg",
            JwtConfigurationProperties::accessAlg
        )
        if (!accessJwtAlgorithm.keyAlgorithm.supportsPublicKey) {
            throw configExceptionOf(
                "$JWT_KEY.access-alg",
                "config.advanced.jwt.access_alg.unsupported_public_key",
                "algorithms" to JwtAlgorithm.entries
                    .filter { it.keyAlgorithm.supportsPublicKey }
                    .joinToString(", ")
            )
        }
        return accessJwtAlgorithm
    }

    private fun getHashConfig(
        properties: HashConfigurationProperties
    ): Pair<HashConfig?, List<ConfigurationException>> {
        val errors = mutableListOf<ConfigurationException>()

        val costParameter = try {
            getCostParameter(properties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val blockSize = try {
            getBlockSize(properties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val parallelizationParameter = try {
            getParallelizationParameter(properties, costParameter)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val saltLengthInBytes = try {
            getSaltLengthInBytes(properties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val keyLengthInBytes = try {
            getKeyLengthInBytes(properties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            val hashConfig = HashConfig(
                costParameter = costParameter!!,
                blockSize = blockSize!!,
                parallelizationParameter = parallelizationParameter!!,
                saltLengthInBytes = saltLengthInBytes!!,
                keyLengthInBytes = keyLengthInBytes!!
            )
            return hashConfig to emptyList()
        } else {
            return null to errors
        }
    }

    private fun getCostParameter(properties: HashConfigurationProperties): Int {
        val key = "$HASH_KEY.cost-parameter"
        val costParameter = parser.getIntOrThrow(
            properties, key,
            HashConfigurationProperties::costParameter
        )
        if (costParameter !in 2..65535 || !isPowerOf2(costParameter)) {
            throw configExceptionOf(key, "config.advanced.hash.invalid_cost_parameter")
        }
        return costParameter
    }

    private fun getBlockSize(properties: HashConfigurationProperties): Int {
        val key = "$HASH_KEY.block-size"
        val blockSize = parser.getIntOrThrow(
            properties, key,
            HashConfigurationProperties::blockSize
        )
        if (blockSize <= 0) {
            throw configExceptionOf(key, "config.advanced.hash.invalid_block_size")
        }
        return blockSize
    }

    private fun getParallelizationParameter(
        properties: HashConfigurationProperties,
        costParameter: Int?
    ): Int? {
        if (costParameter == null) {
            return null
        }

        val key = "$HASH_KEY.parallelization-parameter"
        val max = Int.MAX_VALUE / (128 * costParameter * 8)
        val parallelizationParameter = parser.getIntOrThrow(
            properties, key,
            HashConfigurationProperties::parallelizationParameter
        )
        if (parallelizationParameter !in 1..max) {
            throw configExceptionOf(
                key, "config.advanced.hash.invalid_parallelization_parameter",
                "max" to max
            )
        }
        return parallelizationParameter
    }

    private fun getSaltLengthInBytes(properties: HashConfigurationProperties): Int {
        val key = "$HASH_KEY.salt-length"
        val saltLength = parser.getIntOrThrow(
            properties, key,
            HashConfigurationProperties::saltLength
        )
        if (saltLength <= 0 && saltLength % 8 != 0) {
            throw configExceptionOf(key, "config.advanced.hash.invalid_salt_length")
        }
        return saltLength / 8
    }

    private fun getKeyLengthInBytes(properties: HashConfigurationProperties): Int {
        val key = "$HASH_KEY.key-length"
        val keyLength = parser.getIntOrThrow(
            properties, key,
            HashConfigurationProperties::keyLength
        )
        if (keyLength <= 0) {
            throw configExceptionOf(key, "config.advanced.hash.invalid_key_length")
        }
        return keyLength
    }

    private fun isPowerOf2(var0: Int): Boolean = (var0 and var0 - 1) == 0

    private fun getAuthorizationWebhookConfig(
        properties: AuthorizationWebhookConfigurationProperties
    ): AuthorizationWebhookAdvancedConfig {
        val timeout = parser.getDuration(
            properties, "$AUTHORIZATION_WEBHOOK_KEY.timeout",
            AuthorizationWebhookConfigurationProperties::timeout
        ) ?: DEFAULT_WEBHOOK_TIMEOUT
        return AuthorizationWebhookAdvancedConfig(timeout = timeout)
    }

    companion object {
        private val DEFAULT_WEBHOOK_TIMEOUT = java.time.Duration.ofSeconds(5)
    }

    private fun getValidationCodeConfig(
        properties: ValidationCodeConfigurationProperties
    ): Pair<ValidationCodeConfig?, List<ConfigurationException>> {
        val errors = mutableListOf<ConfigurationException>()

        val expiration = try {
            parser.getDurationOrThrow(
                properties,
                "$VALIDATION_CODE_KEY.expiration", ValidationCodeConfigurationProperties::expiration
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val length = try {
            getValidationCodeLength(properties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val resendDelay = try {
            parser.getDuration(
                properties,
                "$VALIDATION_CODE_KEY.resend-delay", ValidationCodeConfigurationProperties::resendDelay
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            val validationCodeConfig = ValidationCodeConfig(
                expiration = expiration!!,
                length = length!!,
                resendDelay = resendDelay,
            )
            return validationCodeConfig to emptyList()
        } else {
            return null to errors
        }
    }

    private fun getValidationCodeLength(properties: ValidationCodeConfigurationProperties): Int {
        val key = "$HASH_KEY.block-size"
        val blockSize = parser.getIntOrThrow(
            properties, key,
            ValidationCodeConfigurationProperties::length
        )
        if (blockSize <= 0) {
            throw configExceptionOf(key, "config.advanced.validation_code.invalid_length")
        }
        return blockSize
    }
}
