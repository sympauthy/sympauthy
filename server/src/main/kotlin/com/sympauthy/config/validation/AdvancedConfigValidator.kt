package com.sympauthy.config.validation

import com.sympauthy.business.manager.jwt.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.AuthorizationWebhookAdvancedConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.HashConfig
import com.sympauthy.config.model.ValidationCodeConfig
import com.sympauthy.config.parsing.ParsedAdvancedConfig
import com.sympauthy.config.parsing.ParsedHashConfig
import com.sympauthy.config.parsing.ParsedValidationCodeConfig
import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties.Companion.VALIDATION_CODE_KEY
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class AdvancedConfigValidator {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedAdvancedConfig,
        keyGenerationStrategies: Map<String, CryptoKeysGenerationStrategy>
    ): EnabledAdvancedConfig? {
        val keysGenerationStrategy = validateKeysGenerationStrategy(
            ctx, parsed.keysGenerationStrategyId, keyGenerationStrategies
        )
        validatePublicKeyAlgorithm(ctx, parsed.publicJwtAlgorithm)
        validateAccessKeyAlgorithm(ctx, parsed.accessJwtAlgorithm)
        val hashConfig = validateHashConfig(ctx, parsed.hash)
        val validationCodeConfig = validateValidationCodeConfig(ctx, parsed.validationCode)
        val webhookConfig = AuthorizationWebhookAdvancedConfig(
            timeout = parsed.webhookTimeout ?: DEFAULT_WEBHOOK_TIMEOUT
        )

        if (ctx.hasErrors) return null
        return EnabledAdvancedConfig(
            keysGenerationStrategy = keysGenerationStrategy!!,
            publicJwtAlgorithm = parsed.publicJwtAlgorithm!!,
            accessJwtAlgorithm = parsed.accessJwtAlgorithm!!,
            privateJwtAlgorithm = parsed.privateJwtAlgorithm!!,
            hashConfig = hashConfig!!,
            validationCode = validationCodeConfig!!,
            authorizationWebhook = webhookConfig
        )
    }

    private fun validateKeysGenerationStrategy(
        ctx: ConfigParsingContext,
        strategyId: String?,
        keyGenerationStrategies: Map<String, CryptoKeysGenerationStrategy>
    ): CryptoKeysGenerationStrategy? {
        if (strategyId == null) return null
        val strategy = keyGenerationStrategies[strategyId]
        if (strategy == null) {
            ctx.addError(
                configExceptionOf(
                    "$ADVANCED_KEY.keys-generation-strategy",
                    "config.advanced.generation_algorithm.invalid",
                    "algorithm" to strategyId,
                    "algorithms" to keyGenerationStrategies.keys.joinToString(", ")
                )
            )
        }
        return strategy
    }

    private fun validatePublicKeyAlgorithm(ctx: ConfigParsingContext, algorithm: JwtAlgorithm?) {
        if (algorithm != null && !algorithm.keyAlgorithm.supportsPublicKey) {
            ctx.addError(
                configExceptionOf(
                    "$JWT_KEY.public-alg",
                    "config.advanced.jwt.public_alg.unsupported_public_key",
                    "algorithms" to JwtAlgorithm.entries
                        .filter { it.keyAlgorithm.supportsPublicKey }
                        .joinToString(", ")
                )
            )
        }
    }

    private fun validateAccessKeyAlgorithm(ctx: ConfigParsingContext, algorithm: JwtAlgorithm?) {
        if (algorithm != null && !algorithm.keyAlgorithm.supportsPublicKey) {
            ctx.addError(
                configExceptionOf(
                    "$JWT_KEY.access-alg",
                    "config.advanced.jwt.access_alg.unsupported_public_key",
                    "algorithms" to JwtAlgorithm.entries
                        .filter { it.keyAlgorithm.supportsPublicKey }
                        .joinToString(", ")
                )
            )
        }
    }

    private fun validateHashConfig(ctx: ConfigParsingContext, parsed: ParsedHashConfig): HashConfig? {
        val subCtx = ctx.child()

        val costParameter = parsed.costParameter
        if (costParameter != null && (costParameter !in 2..65535 || !isPowerOf2(costParameter))) {
            subCtx.addError(
                configExceptionOf("$HASH_KEY.cost-parameter", "config.advanced.hash.invalid_cost_parameter")
            )
        }

        if (parsed.blockSize != null && parsed.blockSize <= 0) {
            subCtx.addError(
                configExceptionOf("$HASH_KEY.block-size", "config.advanced.hash.invalid_block_size")
            )
        }

        if (costParameter != null && parsed.parallelizationParameter != null) {
            val max = Int.MAX_VALUE / (128 * costParameter * 8)
            if (parsed.parallelizationParameter !in 1..max) {
                subCtx.addError(
                    configExceptionOf(
                        "$HASH_KEY.parallelization-parameter",
                        "config.advanced.hash.invalid_parallelization_parameter",
                        "max" to max
                    )
                )
            }
        }

        if (parsed.saltLength != null && (parsed.saltLength <= 0 && parsed.saltLength % 8 != 0)) {
            subCtx.addError(
                configExceptionOf("$HASH_KEY.salt-length", "config.advanced.hash.invalid_salt_length")
            )
        }

        if (parsed.keyLength != null && parsed.keyLength <= 0) {
            subCtx.addError(
                configExceptionOf("$HASH_KEY.key-length", "config.advanced.hash.invalid_key_length")
            )
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || parsed.costParameter == null || parsed.blockSize == null ||
            parsed.parallelizationParameter == null || parsed.saltLength == null || parsed.keyLength == null
        ) {
            return null
        }
        return HashConfig(
            costParameter = parsed.costParameter,
            blockSize = parsed.blockSize,
            parallelizationParameter = parsed.parallelizationParameter,
            saltLengthInBytes = parsed.saltLength / 8,
            keyLengthInBytes = parsed.keyLength
        )
    }

    private fun validateValidationCodeConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedValidationCodeConfig
    ): ValidationCodeConfig? {
        if (parsed.length != null && parsed.length <= 0) {
            ctx.addError(
                configExceptionOf(
                    "$VALIDATION_CODE_KEY.length",
                    "config.advanced.validation_code.invalid_length"
                )
            )
        }
        if (ctx.hasErrors || parsed.expiration == null || parsed.length == null) {
            return null
        }
        return ValidationCodeConfig(
            expiration = parsed.expiration,
            length = parsed.length,
            resendDelay = parsed.resendDelay
        )
    }

    private fun isPowerOf2(var0: Int): Boolean = (var0 and var0 - 1) == 0

    companion object {
        private val DEFAULT_WEBHOOK_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
