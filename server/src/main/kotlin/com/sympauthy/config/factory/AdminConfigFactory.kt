package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.AdminConfig
import com.sympauthy.config.model.DisabledAdminConfig
import com.sympauthy.config.model.EnabledAdminConfig
import com.sympauthy.config.properties.AdminConfigurationProperties
import com.sympauthy.config.properties.AdminConfigurationProperties.Companion.ADMIN_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AdminConfigFactory(
    @Inject private val parser: ConfigParser
) {

    @Singleton
    fun provideAdminConfig(
        properties: AdminConfigurationProperties
    ): AdminConfig {
        val errors = mutableListOf<ConfigurationException>()

        val enabled = try {
            parser.getBooleanOrThrow(
                properties, "$ADMIN_KEY.enabled",
                AdminConfigurationProperties::enabled
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val integratedUi = try {
            parser.getBooleanOrThrow(
                properties, "$ADMIN_KEY.integrated-ui",
                AdminConfigurationProperties::integratedUi
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            EnabledAdminConfig(
                enabled = enabled!!,
                integratedUi = integratedUi!!
            )
        } else {
            DisabledAdminConfig(errors)
        }
    }
}
