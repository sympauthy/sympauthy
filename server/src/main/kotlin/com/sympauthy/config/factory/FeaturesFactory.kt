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
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

@Factory
class FeaturesFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val mailSender: Optional<MailSender>
) {

    @Singleton
    fun providesFeature(
        properties: FeaturesConfigurationProperties
    ): FeaturesConfig {
        val errors = mutableListOf<ConfigurationException>()

        val emailValidation = try {
            getEmailValidation(properties)
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            EnabledFeaturesConfig(
                emailValidation = emailValidation!!
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
        if (emailValidation && !mailSender.isPresent) {
            throw configExceptionOf(
                key, "config.features.email_validation.no_sender"
            )
        }
        return emailValidation
    }
}
