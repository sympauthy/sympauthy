package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.AuthConfigurationProperties
import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.ByPasswordConfigurationProperties
import com.sympauthy.config.properties.ByPasswordConfigurationProperties.Companion.BY_PASSWORD_KEY
import com.sympauthy.config.properties.TokenConfigurationProperties
import com.sympauthy.config.properties.TokenConfigurationProperties.Companion.TOKEN_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AuthConfigFactory(
    @Inject private val parser: ConfigParser
) {

    @Singleton
    fun provideAuthConfig(
        properties: AuthConfigurationProperties,
        tokenProperties: TokenConfigurationProperties?,
        byPasswordProperties: ByPasswordConfigurationProperties?
    ): AuthConfig {
        val errors = mutableListOf<ConfigurationException>()

        val issuer = try {
            parser.getStringOrThrow(properties, "$AUTH_KEY.issuer", AuthConfigurationProperties::issuer)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val accessExpiration = try {
            tokenProperties?.let {
                parser.getDurationOrThrow(it, "$TOKEN_KEY.access-expiration", TokenConfigurationProperties::accessExpiration)
            }
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val idExpiration = try {
            tokenProperties?.let {
                parser.getDuration(it, "$TOKEN_KEY.id-expiration", TokenConfigurationProperties::idExpiration)
            } ?: accessExpiration
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val refreshEnabled = try {
            tokenProperties?.let {
                parser.getBooleanOrThrow(it, "$TOKEN_KEY.refresh-enabled", TokenConfigurationProperties::refreshEnabled)
            }
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val refreshExpiration = try {
            tokenProperties?.let {
                parser.getDuration(it, "$TOKEN_KEY.refresh-expiration", TokenConfigurationProperties::refreshExpiration)
            }
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val loginClaims = try {
            properties.loginClaims?.map {
                parser.convertToEnum<OpenIdClaim>("$AUTH_KEY.login-claims", it)
            } ?: listOf(OpenIdClaim.EMAIL)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val byPasswordEnabled = try {
            byPasswordProperties?.let {
                parser.getBoolean(it, "$BY_PASSWORD_KEY.enabled", ByPasswordConfigurationProperties::enabled)
            } ?: true
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            EnabledAuthConfig(
                issuer = issuer!!,
                audience = properties.audience,
                token = TokenConfig(
                    accessExpiration = accessExpiration!!,
                    idExpiration = idExpiration!!,
                    refreshEnabled = refreshEnabled!!,
                    refreshExpiration = refreshExpiration
                ),
                loginClaims = loginClaims!!,
                byPassword = ByPasswordConfig(
                    enabled = byPasswordEnabled!!
                )
            )
        } else {
            DisabledAuthConfig(errors)
        }
    }
}
