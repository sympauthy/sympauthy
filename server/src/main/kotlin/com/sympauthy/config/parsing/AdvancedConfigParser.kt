package com.sympauthy.config.parsing

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.security.GeoProvider
import com.sympauthy.business.model.security.IpProvider
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
import com.sympauthy.config.properties.SecurityContextGeoConfigurationProperties.Companion.SECURITY_CONTEXT_GEO_KEY
import com.sympauthy.config.properties.SecurityContextGeoHeadersConfigurationProperties.Companion.GEO_HEADERS_KEY
import com.sympauthy.config.properties.SecurityContextIpConfigurationProperties.Companion.SECURITY_CONTEXT_IP_KEY
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
    val ip: ParsedSecurityContextIpConfig,
    val geo: ParsedSecurityContextGeoConfig
)

data class ParsedSecurityContextIpConfig(
    val provider: ConfiguredImplementation<IpProvider>?,
    val header: String?
)

data class ParsedSecurityContextGeoConfig(
    val autoDetect: Boolean?,
    val providers: List<ParsedGeoProvider>,
    val headers: ParsedSecurityContextGeoHeaders
)

/**
 * An edge a deployment named, and the position it was written at.
 *
 * The position is carried rather than derived because an entry that did not parse is dropped, so
 * what survives is no longer indexed the way the file is — and a refusal naming the wrong line is
 * one an operator corrects in the wrong place.
 */
data class ParsedGeoProvider(
    val index: Int,
    val implementation: ConfiguredImplementation<GeoProvider>
)

data class ParsedSecurityContextGeoHeaders(
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
        ipProperties: SecurityContextIpConfigurationProperties,
        geoProperties: SecurityContextGeoConfigurationProperties,
        geoHeadersProperties: SecurityContextGeoHeadersConfigurationProperties,
        ipProviders: PublishedImplementations<IpProvider>,
        geoProviders: PublishedImplementations<GeoProvider>
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
        val securityContext = ParsedSecurityContextConfig(
            ip = parseSecurityContextIpConfig(ctx, ipProperties, ipProviders),
            geo = parseSecurityContextGeoConfig(ctx, geoProperties, geoHeadersProperties, geoProviders)
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
     * The one proxy a deployment named as nearest, and the header it named instead.
     *
     * A word naming no published edge is refused by the mechanism every setting of this shape shares.
     */
    private fun parseSecurityContextIpConfig(
        ctx: ConfigParsingContext,
        properties: SecurityContextIpConfigurationProperties,
        ipProviders: PublishedImplementations<IpProvider>
    ): ParsedSecurityContextIpConfig {
        val provider = ctx.parse {
            parser.getImplementation(
                properties, "$SECURITY_CONTEXT_IP_KEY.provider", ipProviders,
                SecurityContextIpConfigurationProperties::provider
            )
        }
        val header = ctx.parse {
            parser.getString(
                properties, "$SECURITY_CONTEXT_IP_KEY.header",
                SecurityContextIpConfigurationProperties::header
            )
        }
        return ParsedSecurityContextIpConfig(provider = provider, header = header)
    }

    /**
     * The edges a deployment named for a location, in the order it wrote them.
     *
     * Each is refused against the index it was written at, so a file listing two unknown words
     * reports both. An edge publishing no location is not in the published set, so naming one is
     * refused there rather than accepted to no effect.
     */
    private fun parseSecurityContextGeoConfig(
        ctx: ConfigParsingContext,
        properties: SecurityContextGeoConfigurationProperties,
        headersProperties: SecurityContextGeoHeadersConfigurationProperties,
        geoProviders: PublishedImplementations<GeoProvider>
    ): ParsedSecurityContextGeoConfig {
        val autoDetect = ctx.parse {
            parser.getBoolean(
                properties, "$SECURITY_CONTEXT_GEO_KEY.auto-detect",
                SecurityContextGeoConfigurationProperties::autoDetect
            )
        }
        val providers = properties.providers
            ?.mapIndexedNotNull { index, value ->
                val key = "$SECURITY_CONTEXT_GEO_KEY.providers[$index]"
                ctx.parse { parser.getImplementationOrThrow(properties, key, geoProviders) { value } }
                    ?.let { ParsedGeoProvider(index, it) }
            }
            ?: emptyList()
        return ParsedSecurityContextGeoConfig(
            autoDetect = autoDetect,
            providers = providers,
            headers = parseSecurityContextGeoHeaders(ctx, headersProperties)
        )
    }

    private fun parseSecurityContextGeoHeaders(
        ctx: ConfigParsingContext,
        properties: SecurityContextGeoHeadersConfigurationProperties
    ): ParsedSecurityContextGeoHeaders {
        fun header(key: String, value: (SecurityContextGeoHeadersConfigurationProperties) -> String?) = ctx.parse {
            parser.getString(properties, "$GEO_HEADERS_KEY.$key", value)
        }
        return ParsedSecurityContextGeoHeaders(
            countryCode = header("country-code", SecurityContextGeoHeadersConfigurationProperties::countryCode),
            regionCode = header("region-code", SecurityContextGeoHeadersConfigurationProperties::regionCode),
            region = header("region", SecurityContextGeoHeadersConfigurationProperties::region),
            city = header("city", SecurityContextGeoHeadersConfigurationProperties::city),
            postalCode = header("postal-code", SecurityContextGeoHeadersConfigurationProperties::postalCode),
            timeZone = header("time-zone", SecurityContextGeoHeadersConfigurationProperties::timeZone)
        )
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
