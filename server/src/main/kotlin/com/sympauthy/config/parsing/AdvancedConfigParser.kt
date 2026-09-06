package com.sympauthy.config.parsing

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategyId
import com.sympauthy.business.model.securitycontext.SecurityContextField
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.properties.*
import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties.Companion.AUTHORIZATION_WEBHOOK_KEY
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import com.sympauthy.config.properties.InvitationHashConfigurationProperties.Companion.INVITATION_HASH_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import com.sympauthy.config.properties.PaginationConfigurationProperties.Companion.PAGINATION_KEY
import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties.Companion.VALIDATION_CODE_KEY
import jakarta.inject.Singleton
import java.time.Duration

data class ParsedAdvancedConfig(
    val keysGenerationStrategyId: CryptoKeysGenerationStrategyId?,
    val publicJwtAlgorithm: JwtAlgorithm?,
    val accessJwtAlgorithm: JwtAlgorithm?,
    val privateJwtAlgorithm: JwtAlgorithm?,
    val hash: ParsedHashConfig,
    val invitation: ParsedInvitationConfig,
    val validationCode: ParsedValidationCodeConfig,
    val webhookTimeout: Duration?,
    val pagination: ParsedPaginationConfig,
    val securityContext: ParsedSecurityContextConfig
)

data class ParsedSecurityContextConfig(
    val provider: String?,
    val headers: Map<SecurityContextField, String>,
    val unknownRetention: Duration?,
    val knownRetention: Duration?
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

data class ParsedPaginationConfig(
    val defaultSize: Int?,
    val maxSize: Int?
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
        authorizationWebhookProperties: AuthorizationWebhookConfigurationProperties,
        paginationProperties: PaginationConfigurationProperties,
        securityContextProperties: SecurityContextConfigurationProperties
    ): ParsedAdvancedConfig {
        val keysGenerationStrategyId = ctx.parse {
            parser.getEnumOrThrow<AdvancedConfigurationProperties, CryptoKeysGenerationStrategyId>(
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

        val pagination = parsePaginationConfig(ctx, paginationProperties)
        val securityContext = parseSecurityContextConfig(ctx, securityContextProperties)

        return ParsedAdvancedConfig(
            keysGenerationStrategyId = keysGenerationStrategyId,
            publicJwtAlgorithm = publicJwtAlgorithm,
            accessJwtAlgorithm = accessJwtAlgorithm,
            privateJwtAlgorithm = privateJwtAlgorithm,
            hash = hash,
            invitation = invitation,
            validationCode = validationCode,
            webhookTimeout = webhookTimeout,
            pagination = pagination,
            securityContext = securityContext
        )
    }

    private fun parseSecurityContextConfig(
        ctx: ConfigParsingContext,
        properties: SecurityContextConfigurationProperties
    ): ParsedSecurityContextConfig {
        val subCtx = ctx.child()
        val provider = subCtx.parse {
            parser.getStringOrThrow(
                properties, "$SECURITY_CONTEXT_KEY.provider",
                SecurityContextConfigurationProperties::provider
            )
        }
        val headers = parseHeaderOverrides(subCtx, properties)
        val unknownRetention = subCtx.parse {
            parser.getDurationOrThrow(
                properties, "$SECURITY_CONTEXT_KEY.unknown-retention",
                SecurityContextConfigurationProperties::unknownRetention
            )
        }
        val knownRetention = subCtx.parse {
            parser.getDurationOrThrow(
                properties, "$SECURITY_CONTEXT_KEY.known-retention",
                SecurityContextConfigurationProperties::knownRetention
            )
        }
        ctx.merge(subCtx)
        return ParsedSecurityContextConfig(
            provider = provider,
            headers = headers,
            unknownRetention = unknownRetention,
            knownRetention = knownRetention
        )
    }

    /**
     * The header each field the deployment named an override for is read from.
     *
     * A key naming no field is reported and the rest of the map is still read, so a file with two
     * misspellings reports both.
     */
    private fun parseHeaderOverrides(
        ctx: ConfigParsingContext,
        properties: SecurityContextConfigurationProperties
    ): Map<SecurityContextField, String> {
        return properties.headers.orEmpty().mapNotNull { (name, header) ->
            ctx.parse {
                val key = "$SECURITY_CONTEXT_KEY.headers.$name"
                val field = parser.convertToEnum<SecurityContextField>(key, name)
                if (header.isBlank()) {
                    throw configExceptionOf(key, "config.empty")
                }
                field to header.trim()
            }
        }.toMap()
    }

    private fun parsePaginationConfig(
        ctx: ConfigParsingContext,
        properties: PaginationConfigurationProperties
    ): ParsedPaginationConfig {
        val subCtx = ctx.child()
        val defaultSize = subCtx.parse {
            parser.getIntOrThrow(
                properties, "$PAGINATION_KEY.default-size",
                PaginationConfigurationProperties::defaultSize
            )
        }
        val maxSize = subCtx.parse {
            parser.getIntOrThrow(
                properties, "$PAGINATION_KEY.max-size",
                PaginationConfigurationProperties::maxSize
            )
        }
        ctx.merge(subCtx)
        return ParsedPaginationConfig(
            defaultSize = defaultSize,
            maxSize = maxSize
        )
    }

    private fun parseHashConfig(
        ctx: ConfigParsingContext,
        configKeyPrefix: String,
        properties: HashConfigurationProperties
    ): ParsedHashConfig {
        return parseHashConfigFrom(
            ctx, configKeyPrefix, properties,
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
        return parseHashConfigFrom(
            ctx, INVITATION_HASH_KEY, properties,
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
