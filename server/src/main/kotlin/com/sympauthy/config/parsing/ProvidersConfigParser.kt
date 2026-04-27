package com.sympauthy.config.parsing

import com.jayway.jsonpath.JsonPath
import com.sympauthy.business.model.provider.ProviderUserInfoPathKey
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.properties.ProviderConfigurationProperties
import com.sympauthy.config.properties.ProviderConfigurationProperties.Companion.PROVIDERS_KEY
import jakarta.inject.Singleton
import java.net.URI

data class ParsedProviderConfig(
    val id: String,
    val name: String?,
    val hasOidc: Boolean,
    val hasOAuth2: Boolean,
    // OAuth2 fields
    val oauth2ClientId: String?,
    val oauth2ClientSecret: String?,
    val oauth2Scopes: List<String>?,
    val oauth2AuthorizationUri: URI?,
    val oauth2TokenUri: URI?,
    // OIDC fields
    val oidcClientId: String?,
    val oidcClientSecret: String?,
    val oidcScopes: List<String>,
    val oidcIssuer: URI?,
    // User info fields (for OAuth2 only)
    val userInfoUri: URI?,
    val userInfoPaths: Map<ProviderUserInfoPathKey, JsonPath>?,
    val hasUserInfo: Boolean
)

@Singleton
class ProvidersConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        providers: List<ProviderConfigurationProperties>
    ): List<ParsedProviderConfig> {
        return providers.map { properties ->
            parseProvider(ctx, properties)
        }
    }

    private fun parseProvider(
        ctx: ConfigParsingContext,
        properties: ProviderConfigurationProperties
    ): ParsedProviderConfig {
        val subCtx = ctx.child()
        val keyPrefix = "$PROVIDERS_KEY.${properties.id}"

        val name = subCtx.parse {
            parser.getStringOrThrow(properties, "$keyPrefix.name", ProviderConfigurationProperties::name)
        }

        // Parse OAuth2 config
        var oauth2ClientId: String? = null
        var oauth2ClientSecret: String? = null
        var oauth2Scopes: List<String>? = null
        var oauth2AuthorizationUri: URI? = null
        var oauth2TokenUri: URI? = null
        if (properties.oauth2 != null) {
            val oauth2 = properties.oauth2!!
            val prefix = "$keyPrefix.oauth2"
            oauth2ClientId = subCtx.parse {
                parser.getStringOrThrow(oauth2, "$prefix.client-id", ProviderConfigurationProperties.OAuth2Config::clientId)
            }
            oauth2ClientSecret = subCtx.parse {
                parser.getStringOrThrow(oauth2, "$prefix.client-secret", ProviderConfigurationProperties.OAuth2Config::clientSecret)
            }
            oauth2Scopes = oauth2.scopes
            oauth2AuthorizationUri = subCtx.parse {
                parser.getAbsoluteUriOrThrow(oauth2, "$prefix.authorization-url", ProviderConfigurationProperties.OAuth2Config::authorizationUrl)
            }
            oauth2TokenUri = subCtx.parse {
                parser.getAbsoluteUriOrThrow(oauth2, "$prefix.token-url", ProviderConfigurationProperties.OAuth2Config::tokenUrl)
            }
        }

        // Parse OIDC config
        var oidcClientId: String? = null
        var oidcClientSecret: String? = null
        var oidcIssuer: URI? = null
        var oidcScopes = listOf("openid")
        if (properties.oidc != null) {
            val oidc = properties.oidc!!
            val prefix = "$keyPrefix.oidc"
            oidcClientId = subCtx.parse {
                parser.getStringOrThrow(oidc, "$prefix.client-id", ProviderConfigurationProperties.OpenIdConnectConfig::clientId)
            }
            oidcClientSecret = subCtx.parse {
                parser.getStringOrThrow(oidc, "$prefix.client-secret", ProviderConfigurationProperties.OpenIdConnectConfig::clientSecret)
            }
            oidcIssuer = subCtx.parse {
                parser.getAbsoluteUriOrThrow(oidc, "$prefix.issuer", ProviderConfigurationProperties.OpenIdConnectConfig::issuer)
            }
            oidcScopes = (oidc.scopes ?: listOf("openid")).let { scopes ->
                if ("openid" !in scopes) listOf("openid") + scopes else scopes
            }
        }

        // Parse user info
        var userInfoUri: URI? = null
        var userInfoPaths: Map<ProviderUserInfoPathKey, JsonPath>? = null
        val hasUserInfo = properties.userInfo != null
        if (hasUserInfo) {
            val userInfo = properties.userInfo!!
            userInfoUri = subCtx.parse {
                parser.getAbsoluteUriOrThrow(userInfo, "$keyPrefix.user-info.url", ProviderConfigurationProperties.UserInfoConfig::url)
            }
            userInfoPaths = parseUserInfoPaths(subCtx, userInfo, keyPrefix)
        }

        ctx.merge(subCtx)
        return ParsedProviderConfig(
            id = properties.id,
            name = name,
            hasOidc = properties.oidc != null,
            hasOAuth2 = properties.oauth2 != null,
            oauth2ClientId = oauth2ClientId,
            oauth2ClientSecret = oauth2ClientSecret,
            oauth2Scopes = oauth2Scopes,
            oauth2AuthorizationUri = oauth2AuthorizationUri,
            oauth2TokenUri = oauth2TokenUri,
            oidcClientId = oidcClientId,
            oidcClientSecret = oidcClientSecret,
            oidcScopes = oidcScopes,
            oidcIssuer = oidcIssuer,
            userInfoUri = userInfoUri,
            userInfoPaths = userInfoPaths,
            hasUserInfo = hasUserInfo
        )
    }

    private fun parseUserInfoPaths(
        ctx: ConfigParsingContext,
        userInfo: ProviderConfigurationProperties.UserInfoConfig,
        keyPrefix: String
    ): Map<ProviderUserInfoPathKey, JsonPath>? {
        val userInfoPathsKey = "$keyPrefix.user-info.paths"
        val rawPaths = userInfo.paths
        if (rawPaths == null) {
            ctx.addError(configExceptionOf(userInfoPathsKey, "config.missing"))
            return null
        }
        val paths = rawPaths.mapNotNull { (key, value) ->
            ctx.parse {
                val pathKey = parser.convertToEnum<ProviderUserInfoPathKey>("$userInfoPathsKey.$key", key)
                val rawPath = value ?: throw configExceptionOf(
                    "$userInfoPathsKey.$key", "config.provider.user_info.invalid_value"
                )
                val path = try {
                    JsonPath.compile(rawPath)
                } catch (e: Throwable) {
                    throw ConfigurationException(
                        key = "$userInfoPathsKey.$key",
                        messageId = "config.provider.user_info.invalid_value"
                    )
                }
                pathKey to path
            }
        }.toMap()
        return paths
    }
}
