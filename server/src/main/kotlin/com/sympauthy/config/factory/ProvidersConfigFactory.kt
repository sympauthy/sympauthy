package com.sympauthy.config.factory

import com.jayway.jsonpath.JsonPath
import com.sympauthy.business.model.provider.ProviderUserInfoPathKey
import com.sympauthy.business.model.provider.ProviderUserInfoPathKey.EMAIL
import com.sympauthy.business.model.provider.ProviderUserInfoPathKey.SUB
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.ProviderConfigurationProperties
import com.sympauthy.config.properties.ProviderConfigurationProperties.Companion.PROVIDERS_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

@Factory
class ProvidersConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    @Singleton
    fun provideProvidersConfig(
        providers: List<ProviderConfigurationProperties>
    ): ProvidersConfig {
        val errors = mutableListOf<ConfigurationException>()
        val providerConfigs = mutableListOf<ProviderConfig>()

        for (properties in providers) {
            val providerErrors = mutableListOf<ConfigurationException>()
            val providerConfig = parseProvider(properties, providerErrors)
            if (providerErrors.isEmpty() && providerConfig != null) {
                providerConfigs.add(providerConfig)
            } else {
                errors.addAll(providerErrors)
            }
        }

        return if (errors.isEmpty()) {
            EnabledProvidersConfig(providerConfigs)
        } else {
            DisabledProvidersConfig(errors)
        }
    }

    private fun parseProvider(
        properties: ProviderConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): ProviderConfig? {
        val keyPrefix = "$PROVIDERS_KEY.${properties.id}"

        val name = try {
            parser.getStringOrThrow(properties, "$keyPrefix.name", ProviderConfigurationProperties::name)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val auth = parseAuth(properties, keyPrefix, errors)

        val userInfo = when (auth) {
            is ProviderOpenIdConnectInputConfig, null -> null
            is ProviderOAuth2InputConfig -> parseUserInfo(properties, keyPrefix, errors)
        }

        return if (errors.isEmpty()) {
            ProviderConfig(
                id = properties.id,
                name = name!!,
                auth = auth!!,
                userInfo = userInfo
            )
        } else null
    }

    private fun parseAuth(
        properties: ProviderConfigurationProperties,
        keyPrefix: String,
        errors: MutableList<ConfigurationException>
    ): ProviderAuthInputConfig? {
        return when {
            properties.oidc != null -> parseOpenIdConnect(properties.oidc!!, keyPrefix, errors)
            properties.oauth2 != null -> parseOAuth2(properties.oauth2!!, keyPrefix, errors)
            else -> {
                errors.add(configExceptionOf(keyPrefix, "config.auth.missing"))
                null
            }
        }
    }

    private fun parseOAuth2(
        oauth2: ProviderConfigurationProperties.OAuth2Config,
        keyPrefix: String,
        errors: MutableList<ConfigurationException>
    ): ProviderOAuth2InputConfig? {
        val prefix = "$keyPrefix.oauth2"

        val clientId = try {
            parser.getStringOrThrow(oauth2, "$prefix.client-id", ProviderConfigurationProperties.OAuth2Config::clientId)
        } catch (e: ConfigurationException) { errors.add(e); null }

        val clientSecret = try {
            parser.getStringOrThrow(oauth2, "$prefix.client-secret", ProviderConfigurationProperties.OAuth2Config::clientSecret)
        } catch (e: ConfigurationException) { errors.add(e); null }

        val authorizationUri = try {
            parser.getAbsoluteUriOrThrow(oauth2, "$prefix.authorization-url", ProviderConfigurationProperties.OAuth2Config::authorizationUrl)
        } catch (e: ConfigurationException) { errors.add(e); null }

        val tokenUri = try {
            parser.getAbsoluteUriOrThrow(oauth2, "$prefix.token-url", ProviderConfigurationProperties.OAuth2Config::tokenUrl)
        } catch (e: ConfigurationException) { errors.add(e); null }

        return if (errors.isEmpty()) {
            ProviderOAuth2InputConfig(
                clientId = clientId!!,
                clientSecret = clientSecret!!,
                scopes = oauth2.scopes,
                authorizationUri = authorizationUri!!,
                tokenUri = tokenUri!!
            )
        } else null
    }

    private fun parseOpenIdConnect(
        oidc: ProviderConfigurationProperties.OpenIdConnectConfig,
        keyPrefix: String,
        errors: MutableList<ConfigurationException>
    ): ProviderOpenIdConnectInputConfig? {
        val prefix = "$keyPrefix.oidc"

        val clientId = try {
            parser.getStringOrThrow(oidc, "$prefix.client-id", ProviderConfigurationProperties.OpenIdConnectConfig::clientId)
        } catch (e: ConfigurationException) { errors.add(e); null }

        val clientSecret = try {
            parser.getStringOrThrow(oidc, "$prefix.client-secret", ProviderConfigurationProperties.OpenIdConnectConfig::clientSecret)
        } catch (e: ConfigurationException) { errors.add(e); null }

        val issuer = try {
            parser.getAbsoluteUriOrThrow(oidc, "$prefix.issuer", ProviderConfigurationProperties.OpenIdConnectConfig::issuer)
        } catch (e: ConfigurationException) { errors.add(e); null }

        val scopes = (oidc.scopes ?: listOf("openid")).let { scopes ->
            if ("openid" !in scopes) listOf("openid") + scopes else scopes
        }

        return if (errors.isEmpty()) {
            ProviderOpenIdConnectInputConfig(
                clientId = clientId!!,
                clientSecret = clientSecret!!,
                scopes = scopes,
                issuer = issuer!!
            )
        } else null
    }

    private fun parseUserInfo(
        properties: ProviderConfigurationProperties,
        keyPrefix: String,
        errors: MutableList<ConfigurationException>
    ): ProviderUserInfoConfig? {
        val userInfo = properties.userInfo
        if (userInfo == null) {
            errors.add(configExceptionOf("$keyPrefix.user-info", "config.provider.user_info.missing"))
            return null
        }

        val uri = try {
            parser.getAbsoluteUriOrThrow(
                userInfo,
                "$keyPrefix.user-info.url",
                ProviderConfigurationProperties.UserInfoConfig::url
            )
        } catch (e: ConfigurationException) { errors.add(e); null }

        val paths = parseUserInfoPaths(userInfo, keyPrefix, errors)

        return if (uri != null && paths != null) {
            ProviderUserInfoConfig(uri = uri, paths = paths)
        } else null
    }

    private fun parseUserInfoPaths(
        userInfo: ProviderConfigurationProperties.UserInfoConfig,
        keyPrefix: String,
        errors: MutableList<ConfigurationException>
    ): Map<ProviderUserInfoPathKey, JsonPath>? {
        val userInfoPathsKey = "$keyPrefix.user-info.paths"
        val userInfoPaths = userInfo.paths
        if (userInfoPaths == null) {
            errors.add(configExceptionOf(userInfoPathsKey, "config.missing"))
            return null
        }

        val pathErrors = mutableListOf<ConfigurationException>()
        val paths = userInfoPaths.mapNotNull { (key, value) ->
            try {
                val pathKey = parser.convertToEnum<ProviderUserInfoPathKey>(
                    "$userInfoPathsKey.$key", key
                )
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
            } catch (e: ConfigurationException) {
                pathErrors.add(e)
                null
            }
        }.toMap()

        if (pathErrors.isNotEmpty()) {
            errors.addAll(pathErrors)
            return null
        }

        if (paths[SUB] == null) {
            errors.add(configExceptionOf(
                "$keyPrefix.user-info.paths",
                "config.provider.user_info.missing_subject_key"
            ))
        }

        val authConfig = uncheckedAuthConfig as? EnabledAuthConfig
        if (authConfig?.userMergingEnabled == true && paths[EMAIL] == null) {
            errors.add(configExceptionOf(
                "$keyPrefix.user-info.paths",
                "config.provider.user_info.missing_email_key"
            ))
        }

        return if (errors.isEmpty()) paths else null
    }
}
