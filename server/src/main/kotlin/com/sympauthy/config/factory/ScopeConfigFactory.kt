package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ScopeConfigParser
import com.sympauthy.config.properties.ScopeConfigurationProperties
import com.sympauthy.config.validation.ScopeConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ScopeConfigFactory(
    @Inject private val scopeParser: ScopeConfigParser,
    @Inject private val scopeValidator: ScopeConfigValidator,
    @Inject private val scopeTemplatesConfig: ScopeTemplatesConfig,
    @Inject private val uncheckedAudiencesConfig: AudiencesConfig
) {

    @Singleton
    fun provideScopes(
        propertiesList: List<ScopeConfigurationProperties>
    ): ScopesConfig {
        val enabledTemplatesConfig = scopeTemplatesConfig.orNull()
            ?: return DisabledScopesConfig(emptyList())
        val enabledAudiencesConfig = uncheckedAudiencesConfig as? EnabledAudiencesConfig
            ?: return DisabledScopesConfig(emptyList())

        val ctx = ConfigParsingContext()
        val parsed = scopeParser.parse(ctx, propertiesList, enabledTemplatesConfig.templates)
        val scopes = scopeValidator.validate(
            ctx, parsed, enabledAudiencesConfig.audiences.associateBy { it.id }
        )
        return if (ctx.hasErrors) DisabledScopesConfig(ctx.errors)
        else EnabledScopesConfig(scopes)
    }
}
