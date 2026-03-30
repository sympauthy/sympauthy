package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.DisabledUrlsConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.properties.UrlsConfigurationProperties
import com.sympauthy.config.properties.UrlsConfigurationProperties.Companion.URLS_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class UrlsConfigFactory(
    @Inject private val parser: ConfigParser
) {

    @Singleton
    fun provideUrlsConfig(
        properties: UrlsConfigurationProperties
    ): UrlsConfig {
        val errors = mutableListOf<ConfigurationException>()

        val root = try {
            parser.getAbsoluteUriOrThrow(
                properties, "$URLS_KEY.root",
                UrlsConfigurationProperties::root
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            null
        }

        return if (errors.isEmpty()) {
            EnabledUrlsConfig(
                root = root!!
            )
        } else {
            DisabledUrlsConfig(
                configurationErrors = errors
            )
        }
    }
}
