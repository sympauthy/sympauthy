package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
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
            parser.getBooleanOrThrow(
                properties, "$MFA_KEY.required",
                MfaConfigurationProperties::required
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val totpEnabled = try {
            parser.getBooleanOrThrow(
                totpProperties, "$MFA_TOTP_KEY.enabled",
                MfaTotpConfigurationProperties::enabled
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        if (required == true && totpEnabled == false) {
            errors.add(configExceptionOf("$MFA_TOTP_KEY.enabled", "config.mfa.totp.disabled_when_required"))
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
