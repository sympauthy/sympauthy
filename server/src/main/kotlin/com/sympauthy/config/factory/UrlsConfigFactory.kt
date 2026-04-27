package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.DisabledUrlsConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.parsing.UrlsConfigParser
import com.sympauthy.config.properties.UrlsConfigurationProperties
import com.sympauthy.config.validation.UrlsConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class UrlsConfigFactory(
    @Inject private val urlsParser: UrlsConfigParser,
    @Inject private val urlsValidator: UrlsConfigValidator
) {

    @Singleton
    fun provideUrlsConfig(
        properties: UrlsConfigurationProperties
    ): UrlsConfig {
        val ctx = ConfigParsingContext()
        val parsed = urlsParser.parse(ctx, properties)
        urlsValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledUrlsConfig(ctx.errors)
        else EnabledUrlsConfig(root = parsed.root!!)
    }
}
