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
import com.sympauthy.config.properties.InvitationConfigurationProperties
import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import com.sympauthy.config.properties.InvitationHashConfigurationProperties
import com.sympauthy.config.properties.InvitationHashConfigurationProperties.Companion.INVITATION_HASH_KEY
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
    val invitation: ParsedInvitationConfig,
    val validationCode: ParsedValidationCodeConfig,
    val webhookTimeout: Duration?
)

data class ParsedInvitationConfig(
    val tokenLength: Int?,
    val defaultExpiration: Duration?,
    val maxExpiration: Duration?,
    val hash: ParsedHashConfig
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
        invitationProperties: InvitationConfigurationProperties,
        invitationHashProperties: InvitationHashConfigurationProperties,
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

        val hash = parseHashConfig(ctx, HASH_KEY, hashProperties)
        val invitation = parseInvitationConfig(ctx, invitationProperties, invitationHashProperties)
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
            invitation = invitation,
            validationCode = validationCode,
            webhookTimeout = webhookTimeout
        )
    }

    private fun parseHashConfig(
        ctx: ConfigParsingContext,
        configKeyPrefix: String,
        properties: HashConfigurationProperties
    ): ParsedHashConfig {
        return parseHashConfigFrom(ctx, configKeyPrefix, properties,
            HashConfigurationProperties::costParameter,
            HashConfigurationProperties::blockSize,
            HashConfigurationProperties::parallelizationParameter,
            HashConfigurationProperties::saltLength,
            HashConfigurationProperties::keyLength
        )
    }

    private fun parseInvitationHashConfig(
        ctx: ConfigParsingContext,
        properties: InvitationHashConfigurationProperties
    ): ParsedHashConfig {
        return parseHashConfigFrom(ctx, INVITATION_HASH_KEY, properties,
            InvitationHashConfigurationProperties::costParameter,
            InvitationHashConfigurationProperties::blockSize,
            InvitationHashConfigurationProperties::parallelizationParameter,
            InvitationHashConfigurationProperties::saltLength,
            InvitationHashConfigurationProperties::keyLength
        )
    }

    private fun <C : Any> parseHashConfigFrom(
        ctx: ConfigParsingContext,
        configKeyPrefix: String,
        properties: C,
        costParameterAccessor: (C) -> String?,
        blockSizeAccessor: (C) -> String?,
        parallelizationParameterAccessor: (C) -> String?,
        saltLengthAccessor: (C) -> String?,
        keyLengthAccessor: (C) -> String?
    ): ParsedHashConfig {
        val subCtx = ctx.child()
        val costParameter = subCtx.parse {
            parser.getIntOrThrow(properties, "$configKeyPrefix.cost-parameter", costParameterAccessor)
        }
        val blockSize = subCtx.parse {
            parser.getIntOrThrow(properties, "$configKeyPrefix.block-size", blockSizeAccessor)
        }
        val parallelizationParameter = subCtx.parse {
            parser.getIntOrThrow(
                properties, "$configKeyPrefix.parallelization-parameter", parallelizationParameterAccessor
            )
        }
        val saltLength = subCtx.parse {
            parser.getIntOrThrow(properties, "$configKeyPrefix.salt-length", saltLengthAccessor)
        }
        val keyLength = subCtx.parse {
            parser.getIntOrThrow(properties, "$configKeyPrefix.key-length", keyLengthAccessor)
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

    private fun parseInvitationConfig(
        ctx: ConfigParsingContext,
        properties: InvitationConfigurationProperties,
        hashProperties: InvitationHashConfigurationProperties
    ): ParsedInvitationConfig {
        val subCtx = ctx.child()
        val tokenLength = subCtx.parse {
            parser.getIntOrThrow(
                properties, "$INVITATION_KEY.token-length",
                InvitationConfigurationProperties::tokenLength
            )
        }
        val defaultExpiration = subCtx.parse {
            parser.getDurationOrThrow(
                properties, "$INVITATION_KEY.default-expiration",
                InvitationConfigurationProperties::defaultExpiration
            )
        }
        val maxExpiration = subCtx.parse {
            parser.getDurationOrThrow(
                properties, "$INVITATION_KEY.max-expiration",
                InvitationConfigurationProperties::maxExpiration
            )
        }
        val hash = parseInvitationHashConfig(subCtx, hashProperties)
        ctx.merge(subCtx)
        return ParsedInvitationConfig(
            tokenLength = tokenLength,
            defaultExpiration = defaultExpiration,
            maxExpiration = maxExpiration,
            hash = hash
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
