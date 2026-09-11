package com.sympauthy.config.factory

import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.PublishedImplementationReader
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.DisabledAdvancedConfig
import com.sympauthy.config.parsing.AdvancedConfigParser
import com.sympauthy.config.properties.AdvancedConfigurationProperties
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties
import com.sympauthy.config.properties.HashConfigurationProperties
import com.sympauthy.config.properties.InvitationConfigurationProperties
import com.sympauthy.config.properties.InvitationHashConfigurationProperties
import com.sympauthy.config.properties.JwtConfigurationProperties
import com.sympauthy.config.properties.PaginationConfigurationProperties
import com.sympauthy.config.properties.SecurityContextConfigurationProperties
import com.sympauthy.config.properties.SecurityContextHeadersConfigurationProperties
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties
import com.sympauthy.config.validation.AdvancedConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AdvancedConfigFactory(
    @Inject private val advancedParser: AdvancedConfigParser,
    @Inject private val advancedValidator: AdvancedConfigValidator,
    @Inject private val publishedImplementationReader: PublishedImplementationReader
) {

    @Singleton
    fun provideConfig(
        properties: AdvancedConfigurationProperties,
        jwtProperties: JwtConfigurationProperties,
        hashProperties: HashConfigurationProperties,
        invitationProperties: InvitationConfigurationProperties,
        invitationHashProperties: InvitationHashConfigurationProperties,
        validationCodeProperties: ValidationCodeConfigurationProperties,
        authorizationWebhookProperties: AuthorizationWebhookConfigurationProperties,
        paginationProperties: PaginationConfigurationProperties,
        securityContextProperties: SecurityContextConfigurationProperties,
        securityContextHeadersProperties: SecurityContextHeadersConfigurationProperties,
    ): AdvancedConfig {
        val ctx = ConfigParsingContext()
        val parsed = advancedParser.parse(
            ctx, properties, publishedImplementationReader.read(CryptoKeysGenerationStrategy::class),
            jwtProperties, hashProperties,
            invitationProperties, invitationHashProperties,
            validationCodeProperties, authorizationWebhookProperties, paginationProperties,
            securityContextProperties, securityContextHeadersProperties,
            publishedImplementationReader.read(EdgeProvider::class)
        )
        val config = advancedValidator.validate(ctx, parsed)
        return config ?: DisabledAdvancedConfig(ctx.errors)
    }
}
