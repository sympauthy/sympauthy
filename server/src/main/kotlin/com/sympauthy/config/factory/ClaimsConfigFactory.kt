package com.sympauthy.config.factory

import com.sympauthy.business.model.user.UserMergingStrategy.BY_MAIL
import com.sympauthy.business.model.user.claim.*
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.EMAIL
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ClaimsConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig
) {

    @Singleton
    fun provideClaims(
        propertiesList: List<ClaimConfigurationProperties>
    ): ClaimsConfig {
        val errors = mutableListOf<ConfigurationException>()

        val standardClaims = OpenIdClaim.entries.mapNotNull { openIdClaim ->
            provideStandardClaim(
                properties = propertiesList.firstOrNull { it.id == openIdClaim.id },
                openIdClaim = openIdClaim,
                errors = errors
            )
        }

        val customClaims = propertiesList.mapNotNull { claimProperties ->
            if (OpenIdClaim.entries.none { it.id == claimProperties.id }) {
                provideCustomClaim(
                    properties = claimProperties,
                    claim = claimProperties.id,
                    errors = errors
                )
            } else null
        }

        // Check if claims are properly configured for by-mail user merging strategy.
        // We ignore the check is the config is invalid since to avoid crashing the server.
        if (uncheckedAdvancedConfig is EnabledAdvancedConfig) {
            val emailClaim = standardClaims.firstOrNull { it.id == EMAIL }
            if (uncheckedAdvancedConfig.userMergingStrategy == BY_MAIL && emailClaim == null) {
                errors.add(
                    configExceptionOf(
                        "$CLAIMS_KEY.${EMAIL}",
                        "config.claim.email.disabled"
                    )
                )
            }
        }

        return if (errors.isEmpty()) {
            EnabledClaimsConfig(standardClaims + customClaims)
        } else {
            DisabledClaimsConfig(errors)
        }
    }

    private fun provideStandardClaim(
        properties: ClaimConfigurationProperties?,
        openIdClaim: OpenIdClaim,
        errors: MutableList<ConfigurationException>
    ): Claim? {
        if (properties == null) {
            return null
        }
        val enabled = try {
            parser.getBooleanOrThrow(
                properties, "$CLAIMS_KEY.${openIdClaim.id}.enabled",
                ClaimConfigurationProperties::enabled
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            return null
        }
        val required = try {
            parser.getBoolean(
                properties, "$CLAIMS_KEY.${openIdClaim.id}.required",
                ClaimConfigurationProperties::required
            ) ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            return null
        }
        val allowedValues = getAllowedValues(
            properties = properties,
            key = "$CLAIMS_KEY.${openIdClaim.id}.allowed-values",
            type = openIdClaim.type,
            errors = errors
        )
        return if (enabled) {
            StandardClaim(
                openIdClaim = openIdClaim,
                required = required,
                allowedValues = allowedValues
            )
        } else null
    }

    private fun getAllowedValues(
        properties: ClaimConfigurationProperties,
        key: String,
        type: ClaimDataType,
        errors: MutableList<ConfigurationException>
    ): List<Any>? {
        return properties.allowedValues?.mapIndexedNotNull { index, value ->
            val itemKey = "${key}[${index}]"
            try {
                when (type.typeClass) {
                    String::class -> parser.getString(properties, itemKey) { value }
                    else -> throw configExceptionOf(
                        itemKey, "config.claim.allowed_values.invalid_type",
                        "type" to type.typeClass.simpleName
                    )
                }
            } catch (e: ConfigurationException) {
                errors.add(e)
                null
            }
        }
    }

    private fun provideCustomClaim(
        properties: ClaimConfigurationProperties,
        claim: String,
        errors: MutableList<ConfigurationException>
    ): Claim? {
        val dataType: ClaimDataType = try {
            parser.getEnumOrThrow(
                properties, "$CLAIMS_KEY.$claim.type",
            ) { properties.type }
        } catch (e: ConfigurationException) {
            errors.add(e)
            return null
        }
        val required = try {
            parser.getBoolean(
                properties, "$CLAIMS_KEY.$claim.required",
                ClaimConfigurationProperties::required
            ) ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            return null
        }
        val allowedValues = getAllowedValues(
            properties = properties,
            key = "$CLAIMS_KEY.$claim.allowed-values",
            type = dataType,
            errors = errors
        )
        return CustomClaim(
            id = claim,
            dataType = dataType,
            required = required,
            allowedValues = allowedValues
        )
    }
}
