package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.AuthConfigurationProperties
import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.AuthorizationCodeConfigurationProperties
import com.sympauthy.config.properties.AuthorizationCodeConfigurationProperties.Companion.AUTHORIZATION_CODE_KEY
import com.sympauthy.config.properties.ByPasswordConfigurationProperties
import com.sympauthy.config.properties.ByPasswordConfigurationProperties.Companion.BY_PASSWORD_KEY
import com.sympauthy.config.properties.TokenConfigurationProperties
import com.sympauthy.config.properties.TokenConfigurationProperties.Companion.TOKEN_KEY
import jakarta.inject.Singleton
import java.time.Duration

data class ParsedAuthConfig(
    val issuer: String?,
    val accessExpiration: Duration?,
    val idExpiration: Duration?,
    val refreshEnabled: Boolean?,
    val refreshExpiration: Duration?,
    val dpopRequired: Boolean?,
    val authorizationCodeExpiration: Duration?,
    val identifierClaims: List<String>,
    val userMergingEnabled: Boolean?,
    val byPasswordEnabled: Boolean?
)

@Singleton
class AuthConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: AuthConfigurationProperties,
        tokenProperties: TokenConfigurationProperties?,
        authorizationCodeProperties: AuthorizationCodeConfigurationProperties?,
        byPasswordProperties: ByPasswordConfigurationProperties?
    ): ParsedAuthConfig {
        val issuer = ctx.parse {
            parser.getStringOrThrow(properties, "$AUTH_KEY.issuer", AuthConfigurationProperties::issuer)
        }

        val accessExpiration = ctx.parse {
            tokenProperties?.let {
                parser.getDurationOrThrow(it, "$TOKEN_KEY.access-expiration", TokenConfigurationProperties::accessExpiration)
            }
        }

        val idExpiration = ctx.parse {
            tokenProperties?.let {
                parser.getDuration(it, "$TOKEN_KEY.id-expiration", TokenConfigurationProperties::idExpiration)
            }
        } ?: accessExpiration

        val refreshEnabled = ctx.parse {
            tokenProperties?.let {
                parser.getBooleanOrThrow(it, "$TOKEN_KEY.refresh-enabled", TokenConfigurationProperties::refreshEnabled)
            }
        }

        val refreshExpiration = ctx.parse {
            tokenProperties?.let {
                parser.getDuration(it, "$TOKEN_KEY.refresh-expiration", TokenConfigurationProperties::refreshExpiration)
            }
        }

        val dpopRequired = ctx.parse {
            tokenProperties?.let {
                parser.getBoolean(it, "$TOKEN_KEY.dpop-required", TokenConfigurationProperties::dpopRequired)
            }
        }

        val authorizationCodeExpiration = ctx.parse {
            authorizationCodeProperties?.let {
                parser.getDurationOrThrow(
                    it, "$AUTHORIZATION_CODE_KEY.expiration",
                    AuthorizationCodeConfigurationProperties::expiration
                )
            }
        }

        val identifierClaims = properties.identifierClaims
            ?.mapIndexedNotNull { index, value ->
                val key = "$AUTH_KEY.identifier-claims[$index]"
                ctx.parse { parser.getStringOrThrow(properties, key) { value } }
            }
            ?: emptyList()

        val userMergingEnabled = ctx.parse {
            parser.getBooleanOrThrow(
                properties, "$AUTH_KEY.user-merging-enabled",
                AuthConfigurationProperties::userMergingEnabled
            )
        }

        val byPasswordEnabled = ctx.parse {
            byPasswordProperties?.let {
                parser.getBoolean(it, "$BY_PASSWORD_KEY.enabled", ByPasswordConfigurationProperties::enabled)
            }
        } ?: false

        return ParsedAuthConfig(
            issuer = issuer,
            accessExpiration = accessExpiration,
            idExpiration = idExpiration,
            refreshEnabled = refreshEnabled,
            refreshExpiration = refreshExpiration,
            dpopRequired = dpopRequired,
            authorizationCodeExpiration = authorizationCodeExpiration,
            identifierClaims = identifierClaims,
            userMergingEnabled = userMergingEnabled,
            byPasswordEnabled = byPasswordEnabled
        )
    }
}
