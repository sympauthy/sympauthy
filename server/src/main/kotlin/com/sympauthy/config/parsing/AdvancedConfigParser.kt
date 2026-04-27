package com.sympauthy.config.parsing

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.AdvancedConfigurationProperties
import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties.Companion.AUTHORIZATION_WEBHOOK_KEY
import com.sympauthy.config.properties.HashConfigurationProperties
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties.Companion.VALIDATION_CODE_KEY
import jakarta.inject.Singleton
import java.time.Duration

data class ParsedAdvancedConfig(
    val keysGenerationStrategyId: String?,
    val publicJwtAlgorithm: JwtAlgorithm?,
    val accessJwtAlgorithm: JwtAlgorithm?,
    val privateJwtAlgorithm: JwtAlgorithm?,
    val hash: ParsedHashConfig,
    val validationCode: ParsedValidationCodeConfig,
    val webhookTimeout: Duration?
)

data class ParsedHashConfig(
    val costParameter: Int?,
    val blockSize: Int?,
    val parallelizationParameter: Int?,
    val saltLength: Int?,
    val keyLength: Int?
)

data class ParsedValidationCodeConfig(
    val expiration: Duration?,
    val length: Int?,
    val resendDelay: Duration?
)

@Singleton
class AdvancedConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: AdvancedConfigurationProperties,
        jwtProperties: JwtConfigurationProperties,
        hashProperties: HashConfigurationProperties,
        validationCodeProperties: ValidationCodeConfigurationProperties,
        authorizationWebhookProperties: AuthorizationWebhookConfigurationProperties
    ): ParsedAdvancedConfig {
        val keysGenerationStrategyId = ctx.parse {
            parser.getString(
                properties, "$ADVANCED_KEY.keys-generation-strategy",
                AdvancedConfigurationProperties::keysGenerationStrategy
            )
        }

        val publicJwtAlgorithm = ctx.parse {
            parser.getEnumOrThrow<JwtConfigurationProperties, JwtAlgorithm>(
                jwtProperties, "$JWT_KEY.public-alg",
                JwtConfigurationProperties::publicAlg
            )
        }

        val accessJwtAlgorithm = ctx.parse {
            parser.getEnumOrThrow<JwtConfigurationProperties, JwtAlgorithm>(
                jwtProperties, "$JWT_KEY.access-alg",
                JwtConfigurationProperties::accessAlg
            )
        }

        val privateJwtAlgorithm = ctx.parse {
            parser.getEnumOrThrow<JwtConfigurationProperties, JwtAlgorithm>(
                jwtProperties, "$JWT_KEY.private-alg",
                JwtConfigurationProperties::privateAlg
            )
        }

        val hash = parseHashConfig(ctx, hashProperties)
        val validationCode = parseValidationCodeConfig(ctx, validationCodeProperties)

        val webhookTimeout = ctx.parse {
            parser.getDuration(
                authorizationWebhookProperties, "$AUTHORIZATION_WEBHOOK_KEY.timeout",
                AuthorizationWebhookConfigurationProperties::timeout
            )
        }

        return ParsedAdvancedConfig(
            keysGenerationStrategyId = keysGenerationStrategyId,
            publicJwtAlgorithm = publicJwtAlgorithm,
            accessJwtAlgorithm = accessJwtAlgorithm,
            privateJwtAlgorithm = privateJwtAlgorithm,
            hash = hash,
            validationCode = validationCode,
            webhookTimeout = webhookTimeout
        )
    }

    private fun parseHashConfig(
        ctx: ConfigParsingContext,
        properties: HashConfigurationProperties
    ): ParsedHashConfig {
        val subCtx = ctx.child()
        val costParameter = subCtx.parse {
            parser.getIntOrThrow(properties, "$HASH_KEY.cost-parameter", HashConfigurationProperties::costParameter)
        }
        val blockSize = subCtx.parse {
            parser.getIntOrThrow(properties, "$HASH_KEY.block-size", HashConfigurationProperties::blockSize)
        }
        val parallelizationParameter = subCtx.parse {
            parser.getIntOrThrow(
                properties, "$HASH_KEY.parallelization-parameter",
                HashConfigurationProperties::parallelizationParameter
            )
        }
        val saltLength = subCtx.parse {
            parser.getIntOrThrow(properties, "$HASH_KEY.salt-length", HashConfigurationProperties::saltLength)
        }
        val keyLength = subCtx.parse {
            parser.getIntOrThrow(properties, "$HASH_KEY.key-length", HashConfigurationProperties::keyLength)
        }
        ctx.merge(subCtx)
        return ParsedHashConfig(
            costParameter = costParameter,
            blockSize = blockSize,
            parallelizationParameter = parallelizationParameter,
            saltLength = saltLength,
            keyLength = keyLength
        )
    }

    private fun parseValidationCodeConfig(
        ctx: ConfigParsingContext,
        properties: ValidationCodeConfigurationProperties
    ): ParsedValidationCodeConfig {
        val subCtx = ctx.child()
        val expiration = subCtx.parse {
            parser.getDurationOrThrow(
                properties, "$VALIDATION_CODE_KEY.expiration",
                ValidationCodeConfigurationProperties::expiration
            )
        }
        val length = subCtx.parse {
            parser.getIntOrThrow(
                properties, "$VALIDATION_CODE_KEY.length",
                ValidationCodeConfigurationProperties::length
            )
        }
        val resendDelay = subCtx.parse {
            parser.getDuration(
                properties, "$VALIDATION_CODE_KEY.resend-delay",
                ValidationCodeConfigurationProperties::resendDelay
            )
        }
        ctx.merge(subCtx)
        return ParsedValidationCodeConfig(
            expiration = expiration,
            length = length,
            resendDelay = resendDelay
        )
    }
}
