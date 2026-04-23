package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.AuthConfigurationProperties
import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.AuthorizationCodeConfigurationProperties
import com.sympauthy.config.properties.AuthorizationCodeConfigurationProperties.Companion.AUTHORIZATION_CODE_KEY
import com.sympauthy.config.properties.ByPasswordConfigurationProperties
import com.sympauthy.config.properties.ByPasswordConfigurationProperties.Companion.BY_PASSWORD_KEY
import com.sympauthy.config.properties.TokenConfigurationProperties
import com.sympauthy.config.properties.TokenConfigurationProperties.Companion.TOKEN_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AuthConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Singleton
    fun provideAuthConfig(
        properties: AuthConfigurationProperties,
        tokenProperties: TokenConfigurationProperties?,
        authorizationCodeProperties: AuthorizationCodeConfigurationProperties?,
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

        val audience = properties.audience
            ?: uncheckedUrlsConfig.getOrNull()?.root?.toString()

        if (audience == null) {
            errors.add(
                configExceptionOf(
                    "$AUTH_KEY.audience",
                    "config.auth.audience.missing"
                )
            )
        }

        val accessExpiration = try {
            tokenProperties?.let {
                parser.getDurationOrThrow(
                    it,
                    "$TOKEN_KEY.access-expiration",
                    TokenConfigurationProperties::accessExpiration
                )
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

        val dpopRequired = try {
            tokenProperties?.let {
                parser.getBoolean(it, "$TOKEN_KEY.dpop-required", TokenConfigurationProperties::dpopRequired)
            }
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val authorizationCodeExpiration = try {
            authorizationCodeProperties?.let {
                parser.getDurationOrThrow(
                    it, "$AUTHORIZATION_CODE_KEY.expiration",
                    AuthorizationCodeConfigurationProperties::expiration
                )
            }
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val identifierClaims = parseIdentifierClaims(properties, uncheckedClaimsConfig, errors)

        val userMergingEnabled = try {
            parser.getBooleanOrThrow(
                properties, "$AUTH_KEY.user-merging-enabled",
                AuthConfigurationProperties::userMergingEnabled
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val byPasswordEnabled = try {
            byPasswordProperties?.let {
                parser.getBoolean(it, "$BY_PASSWORD_KEY.enabled", ByPasswordConfigurationProperties::enabled)
            } ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        if (byPasswordEnabled == true && identifierClaims.isNullOrEmpty()) {
            errors.add(
                configExceptionOf(
                    "$BY_PASSWORD_KEY.enabled",
                    "config.auth.by_password.no_identifier_claim"
                )
            )
        }

        return if (errors.isEmpty()) {
            EnabledAuthConfig(
                issuer = issuer!!,
                audience = audience!!,
                token = TokenConfig(
                    accessExpiration = accessExpiration!!,
                    idExpiration = idExpiration!!,
                    refreshEnabled = refreshEnabled!!,
                    refreshExpiration = refreshExpiration,
                    dpopRequired = dpopRequired ?: false
                ),
                authorizationCode = AuthorizationCodeConfig(
                    expiration = authorizationCodeExpiration!!,
                ),
                identifierClaims = identifierClaims!!,
                userMergingEnabled = userMergingEnabled!!,
                byPassword = ByPasswordConfig(
                    enabled = byPasswordEnabled!!
                )
            )
        } else {
            DisabledAuthConfig(errors)
        }
    }

    private fun parseIdentifierClaims(
        properties: AuthConfigurationProperties,
        uncheckedClaimsConfig: ClaimsConfig,
        errors: MutableList<ConfigurationException>
    ): List<String>? {
        val identifierClaimsErrors = mutableListOf<ConfigurationException>()
        val identifierClaims = properties.identifierClaims
            ?.mapIndexedNotNull { index, value ->
                val key = "$AUTH_KEY.identifier-claims[$index]"
                try {
                    parser.getStringOrThrow(properties, key) { value }
                } catch (e: ConfigurationException) {
                    identifierClaimsErrors.add(e)
                    null
                }
            }
            ?: emptyList()

        // Validate that each identifier claim is enabled in the claims configuration.
        val enabledClaimsConfig = uncheckedClaimsConfig as? EnabledClaimsConfig
        if (enabledClaimsConfig != null) {
            val enabledClaimIds = enabledClaimsConfig.claims
                .filter { it.enabled }
                .map { it.id }
                .toSet()
            identifierClaims.forEach { identifierClaimId ->
                if (identifierClaimId !in enabledClaimIds) {
                    identifierClaimsErrors.add(
                        configExceptionOf(
                            "$AUTH_KEY.identifier-claims",
                            "config.auth.identifier_claim.disabled",
                            "claim" to identifierClaimId
                        )
                    )
                }
            }

            return if (identifierClaimsErrors.isEmpty()) {
                identifierClaims
            } else {
                errors.addAll(identifierClaimsErrors)
                null
            }
        }

        return identifierClaims
    }
}
