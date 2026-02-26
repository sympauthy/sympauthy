package com.sympauthy.config.factory

import com.sympauthy.business.manager.mail.MailSender
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.DisabledFeaturesConfig
import com.sympauthy.config.model.EnabledFeaturesConfig
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.properties.FeaturesConfigurationProperties
import com.sympauthy.config.properties.FeaturesConfigurationProperties.Companion.FEATURES_KEY
import io.micronaut.context.annotation.Factory
import jakarta.annotation.Nullable
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class FeaturesConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject @param:Nullable private val mailSender: MailSender?
) {

    @Singleton
    fun providesFeature(
        propertiesList: FeaturesConfigurationProperties
    ): FeaturesConfig {
        val errors = mutableListOf<ConfigurationException>()

        val allowAccessToClientWithoutScope = try {
            parser.getBooleanOrThrow(
                propertiesList, "$FEATURES_KEY.allow-access-to-client-without-scope",
                FeaturesConfigurationProperties::allowAccessToClientWithoutScope
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val emailValidation = try {
            getEmailValidation(propertiesList)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val grantUnhandledScopes = try {
            parser.getBooleanOrThrow(
                propertiesList, "$FEATURES_KEY.grant-unhandled-scopes",
                FeaturesConfigurationProperties::grantUnhandledScopes
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        val printDetailsInError = try {
            parser.getBooleanOrThrow(
                propertiesList, "$FEATURES_KEY.print-details-in-error",
                FeaturesConfigurationProperties::printDetailsInError
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            EnabledFeaturesConfig(
                allowAccessToClientWithoutScope = allowAccessToClientWithoutScope!!,
                emailValidation = emailValidation!!,
                grantUnhandledScopes = grantUnhandledScopes!!,
                printDetailsInError = printDetailsInError!!
            )
        } else {
            DisabledFeaturesConfig(errors)
        }
    }

    private fun getEmailValidation(properties: FeaturesConfigurationProperties): Boolean {
        val key = "$FEATURES_KEY.email-validation"
        val emailValidation = parser.getBooleanOrThrow(
            properties, key,
            FeaturesConfigurationProperties::emailValidation
        )
        if (emailValidation && mailSender == null) {
            throw configExceptionOf(
                key, "config.features.email_validation.no_sender"
            )
        }
        return emailValidation
    }
}
