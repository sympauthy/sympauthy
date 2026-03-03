package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.DisabledMfaConfig
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.properties.MfaConfigurationProperties
import com.sympauthy.config.properties.MfaConfigurationProperties.Companion.MFA_KEY
import com.sympauthy.config.properties.MfaTotpConfigurationProperties
import com.sympauthy.config.properties.MfaTotpConfigurationProperties.Companion.MFA_TOTP_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class MfaConfigFactory(
    @Inject private val parser: ConfigParser
) {

    @Singleton
    fun provideMfaConfig(
        properties: MfaConfigurationProperties,
        totpProperties: MfaTotpConfigurationProperties
    ): MfaConfig {
        val errors = mutableListOf<ConfigurationException>()

        val required = try {
            parser.getBoolean(
                properties, "$MFA_KEY.required",
                MfaConfigurationProperties::required
            ) ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val totpEnabled = try {
            parser.getBoolean(
                totpProperties, "$MFA_TOTP_KEY.enabled",
                MfaTotpConfigurationProperties::enabled
            ) ?: false
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            EnabledMfaConfig(
                required = required!!,
                totp = totpEnabled!!
            )
        } else {
            DisabledMfaConfig(errors)
        }
    }
}
