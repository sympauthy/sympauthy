package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
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
        byPasswordProperties: ByPasswordConfigurationProperties?,
        uncheckedClaimsConfig: ClaimsConfig
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

        val loginClaims = parseLoginClaims(properties, uncheckedClaimsConfig, errors)

        val byPasswordEnabled = try {
            byPasswordProperties?.let {
                parser.getBoolean(it, "$BY_PASSWORD_KEY.enabled", ByPasswordConfigurationProperties::enabled)
            } ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        if (byPasswordEnabled == true && loginClaims.isNullOrEmpty()) {
            errors.add(
                configExceptionOf(
                    "$BY_PASSWORD_KEY.enabled",
                    "config.auth.by_password.no_login_claim"
                )
            )
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

    private fun parseLoginClaims(
        properties: AuthConfigurationProperties,
        uncheckedClaimsConfig: ClaimsConfig,
        errors: MutableList<ConfigurationException>
    ): List<OpenIdClaim>? {
        val loginClaimsErrors = mutableListOf<ConfigurationException>()
        val loginClaims = try {
            properties.loginClaims?.map {
                parser.convertToEnum<OpenIdClaim>("$AUTH_KEY.login-claims", it)
            } ?: emptyList()
        } catch (e: ConfigurationException) {
            loginClaimsErrors.add(e)
            null
        }

        // Validate that each login claim is enabled in the claims configuration.
        val enabledClaimsConfig = uncheckedClaimsConfig as? EnabledClaimsConfig
        if (enabledClaimsConfig != null) {
            val enabledClaimIds = enabledClaimsConfig.claims
                .filter { it.enabled }
                .map { it.id }
                .toSet()
            loginClaims?.forEach { loginClaim ->
                if (loginClaim.id !in enabledClaimIds) {
                    loginClaimsErrors.add(
                        configExceptionOf(
                            "$AUTH_KEY.login-claims",
                            "config.auth.login_claim.disabled",
                            "claim" to loginClaim.id
                        )
                    )
                }
            }

            return if (errors.isEmpty()) {
                loginClaims
            } else {
                errors.addAll(loginClaimsErrors)
                null
            }
        }

        return loginClaims
    }
}
