package com.sympauthy.config.factory

import com.sympauthy.business.manager.jwt.CryptoKeysGenerationStrategy
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.DisabledAdvancedConfig
import com.sympauthy.config.parsing.AdvancedConfigParser
import com.sympauthy.config.properties.AdvancedConfigurationProperties
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties
import com.sympauthy.config.properties.HashConfigurationProperties
import com.sympauthy.config.properties.JwtConfigurationProperties
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties
import com.sympauthy.config.validation.AdvancedConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AdvancedConfigFactory(
    @Inject private val advancedParser: AdvancedConfigParser,
    @Inject private val advancedValidator: AdvancedConfigValidator
) {

    @Singleton
    fun provideConfig(
        properties: AdvancedConfigurationProperties,
        jwtProperties: JwtConfigurationProperties,
        hashProperties: HashConfigurationProperties,
        validationCodeProperties: ValidationCodeConfigurationProperties,
        authorizationWebhookProperties: AuthorizationWebhookConfigurationProperties,
        keyGenerationStrategies: Map<String, CryptoKeysGenerationStrategy>,
    ): AdvancedConfig {
        val ctx = ConfigParsingContext()
        val parsed = advancedParser.parse(
            ctx, properties, jwtProperties, hashProperties,
            validationCodeProperties, authorizationWebhookProperties
        )
        val config = advancedValidator.validate(ctx, parsed, keyGenerationStrategies)
        return config ?: DisabledAdvancedConfig(ctx.errors)
    }
}
