package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.DisabledProvidersConfig
import com.sympauthy.config.model.EnabledProvidersConfig
import com.sympauthy.config.model.ProvidersConfig
import com.sympauthy.config.parsing.ProvidersConfigParser
import com.sympauthy.config.properties.ProviderConfigurationProperties
import com.sympauthy.config.validation.ProvidersConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ProvidersConfigFactory(
    @Inject private val providersParser: ProvidersConfigParser,
    @Inject private val providersValidator: ProvidersConfigValidator
) {

    @Singleton
    fun provideProvidersConfig(
        providers: List<ProviderConfigurationProperties>
    ): ProvidersConfig {
        val ctx = ConfigParsingContext()
        val parsed = providersParser.parse(ctx, providers)
        val providerConfigs = providersValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledProvidersConfig(ctx.errors)
        else EnabledProvidersConfig(providerConfigs)
    }
}
