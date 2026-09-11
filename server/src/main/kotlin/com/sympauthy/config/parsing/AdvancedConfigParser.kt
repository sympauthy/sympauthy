package com.sympauthy.config.parsing

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.PublishedImplementations
import com.sympauthy.config.model.ConfiguredImplementation
import com.sympauthy.config.properties.*
import com.sympauthy.config.properties.AdvancedConfigurationProperties.Companion.ADVANCED_KEY
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties.Companion.AUTHORIZATION_WEBHOOK_KEY
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import com.sympauthy.config.properties.InvitationHashConfigurationProperties.Companion.INVITATION_HASH_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import com.sympauthy.config.properties.PaginationConfigurationProperties.Companion.PAGINATION_KEY
import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import com.sympauthy.config.properties.SecurityContextHeadersConfigurationProperties.Companion.HEADERS_KEY
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties.Companion.VALIDATION_CODE_KEY
import jakarta.inject.Singleton
import java.time.Duration

data class ParsedAdvancedConfig(
    val keysGenerationStrategy: ConfiguredImplementation<CryptoKeysGenerationStrategy>?,
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
    val autoDetect: Boolean?,
    val providers: List<ConfiguredImplementation<EdgeProvider>>,
    val headers: ParsedSecurityContextHeaders
)

data class ParsedSecurityContextHeaders(
    val clientIp: String?,
    val countryCode: String?,
    val regionCode: String?,
    val region: String?,
    val city: String?,
    val postalCode: String?,
    val timeZone: String?
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
        keysGenerationStrategies: PublishedImplementations<CryptoKeysGenerationStrategy>,
        jwtProperties: JwtConfigurationProperties,
        hashProperties: HashConfigurationProperties,
        invitationProperties: InvitationConfigurationProperties,
        invitationHashProperties: InvitationHashConfigurationProperties,
        validationCodeProperties: ValidationCodeConfigurationProperties,
        authorizationWebhookProperties: AuthorizationWebhookConfigurationProperties,
        paginationProperties: PaginationConfigurationProperties,
        securityContextProperties: SecurityContextConfigurationProperties,
        securityContextHeadersProperties: SecurityContextHeadersConfigurationProperties,
        edgeProviders: PublishedImplementations<EdgeProvider>
    ): ParsedAdvancedConfig {
        val keysGenerationStrategy = ctx.parse {
            parser.getImplementationOrThrow(
                properties, "$ADVANCED_KEY.keys-generation-strategy", keysGenerationStrategies,
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
        val securityContext = parseSecurityContextConfig(
            ctx, securityContextProperties, securityContextHeadersProperties, edgeProviders
        )

        return ParsedAdvancedConfig(
            keysGenerationStrategy = keysGenerationStrategy,
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

    /**
     * The edges a deployment named, in the order it wrote them, and the headers it named for itself.
     *
     * A word naming no published provider is refused by the mechanism every setting of this shape
     * shares, against the index it was written at, so a file listing two unknown words reports both.
     */
    private fun parseSecurityContextConfig(
        ctx: ConfigParsingContext,
        properties: SecurityContextConfigurationProperties,
        headersProperties: SecurityContextHeadersConfigurationProperties,
        edgeProviders: PublishedImplementations<EdgeProvider>
    ): ParsedSecurityContextConfig {
        val subCtx = ctx.child()
        val autoDetect = subCtx.parse {
            parser.getBoolean(
                properties, "$SECURITY_CONTEXT_KEY.auto-detect",
                SecurityContextConfigurationProperties::autoDetect
            )
        }
        val providers = properties.providers
            ?.mapIndexedNotNull { index, value ->
                val key = "$SECURITY_CONTEXT_KEY.providers[$index]"
                subCtx.parse { parser.getImplementationOrThrow(properties, key, edgeProviders) { value } }
            }
            ?: emptyList()
        val headers = parseSecurityContextHeaders(subCtx, headersProperties)
        ctx.merge(subCtx)
        return ParsedSecurityContextConfig(
            autoDetect = autoDetect,
            providers = providers,
            headers = headers
        )
    }

    private fun parseSecurityContextHeaders(
        ctx: ConfigParsingContext,
        properties: SecurityContextHeadersConfigurationProperties
    ): ParsedSecurityContextHeaders {
        val subCtx = ctx.child()
        fun header(key: String, value: (SecurityContextHeadersConfigurationProperties) -> String?) = subCtx.parse {
            parser.getString(properties, "$HEADERS_KEY.$key", value)
        }
        val parsed = ParsedSecurityContextHeaders(
            clientIp = header("client-ip", SecurityContextHeadersConfigurationProperties::clientIp),
            countryCode = header("country-code", SecurityContextHeadersConfigurationProperties::countryCode),
            regionCode = header("region-code", SecurityContextHeadersConfigurationProperties::regionCode),
            region = header("region", SecurityContextHeadersConfigurationProperties::region),
            city = header("city", SecurityContextHeadersConfigurationProperties::city),
            postalCode = header("postal-code", SecurityContextHeadersConfigurationProperties::postalCode),
            timeZone = header("time-zone", SecurityContextHeadersConfigurationProperties::timeZone)
        )
        ctx.merge(subCtx)
        return parsed
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
