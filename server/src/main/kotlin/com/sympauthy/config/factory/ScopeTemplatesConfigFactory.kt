package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.DisabledScopeTemplatesConfig
import com.sympauthy.config.model.EnabledScopeTemplatesConfig
import com.sympauthy.config.model.ScopeTemplatesConfig
import com.sympauthy.config.parsing.ScopeTemplatesConfigParser
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties
import com.sympauthy.config.validation.ScopeTemplatesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ScopeTemplatesConfigFactory(
    @Inject private val scopeTemplatesParser: ScopeTemplatesConfigParser,
    @Inject private val scopeTemplatesValidator: ScopeTemplatesConfigValidator
) {

    @Singleton
    fun provideScopeTemplates(
        templatesList: List<ScopeTemplateConfigurationProperties>
    ): ScopeTemplatesConfig {
        val ctx = ConfigParsingContext()
        val parsed = scopeTemplatesParser.parse(ctx, templatesList)
        val templates = scopeTemplatesValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledScopeTemplatesConfig(ctx.errors)
        else EnabledScopeTemplatesConfig(templates)
    }
}
